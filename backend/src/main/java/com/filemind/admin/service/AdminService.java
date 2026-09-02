package com.filemind.admin.service;

import com.filemind.admin.dto.AdminDocumentOversightResponse;
import com.filemind.admin.dto.AdminOverviewResponse;
import com.filemind.admin.dto.AdminSystemHealthResponse;
import com.filemind.admin.dto.AdminUserResponse;
import com.filemind.common.exception.BadRequestException;
import com.filemind.common.exception.ResourceNotFoundException;
import com.filemind.document.entity.Document;
import com.filemind.document.model.ProcessingStatus;
import com.filemind.document.repository.DocumentRepository;
import com.filemind.user.entity.User;
import com.filemind.user.entity.UserRole;
import com.filemind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        List<User> users = userRepository.findAll();
        List<Document> documents = documentRepository.findAll();

        long totalUsers = users.size();
        long adminUsers = users.stream().filter(user -> user.getRole() == UserRole.ADMIN).count();
        long failedDocuments = documents.stream()
            .filter(document -> document.getProcessingStatus() == ProcessingStatus.FAILED)
            .count();
        long processingDocuments = documents.stream()
            .filter(document -> document.getProcessingStatus() != ProcessingStatus.COMPLETED)
            .count();

        List<AdminUserResponse> userResponses = users.stream()
            .sorted(Comparator.comparing(User::getCreatedAt).reversed())
            .map(user -> AdminUserResponse.from(user, documentRepository.findByOwnerIdAndDeletedFalseOrderByUpdatedAtDesc(user.getId()).size()))
            .toList();

        List<AdminDocumentOversightResponse> documentResponses = documents.stream()
            .filter(document -> !document.isDeleted() && document.getOwner() != null)
            .sorted(Comparator.comparing(Document::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .map(document -> new AdminDocumentOversightResponse(
                document.getId(),
                document.getName(),
                document.getOwner().getFullName(),
                document.getOwner().getEmail(),
                document.getProcessingStatus() != null ? document.getProcessingStatus().name() : "UNKNOWN",
                documentRepository.findByOwnerIdAndDeletedFalseOrderByUpdatedAtDesc(document.getOwner().getId()).size(),
                0L,
                document.getUpdatedAt()
            ))
            .toList();

        AdminSystemHealthResponse health = new AdminSystemHealthResponse(
            "UP",
            "UP",
            "UP",
            failedDocuments,
            processingDocuments,
            totalUsers
        );

        return new AdminOverviewResponse(
            totalUsers,
            adminUsers,
            documents.size(),
            failedDocuments,
            processingDocuments,
            health,
            userResponses,
            documentResponses
        );
    }

    @Transactional
    public AdminUserResponse updateUserRole(String userId, String roleInput) {
        UserRole role;
        try {
            role = UserRole.valueOf(roleInput.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Role must be ADMIN or USER");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setRole(role);
        userRepository.save(user);

        return AdminUserResponse.from(user, documentRepository.findByOwnerIdAndDeletedFalseOrderByUpdatedAtDesc(user.getId()).size());
    }
}
