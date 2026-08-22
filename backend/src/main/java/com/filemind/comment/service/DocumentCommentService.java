package com.filemind.comment.service;

import com.filemind.activity.entity.ActivityAction;
import com.filemind.activity.service.ActivityService;
import com.filemind.collaboration.service.DocumentAccessService;
import com.filemind.comment.dto.CreateDocumentCommentRequest;
import com.filemind.comment.dto.DocumentCommentResponse;
import com.filemind.comment.entity.DocumentComment;
import com.filemind.comment.repository.DocumentCommentRepository;
import com.filemind.common.exception.BadRequestException;
import com.filemind.document.entity.Document;
import com.filemind.user.entity.User;
import com.filemind.version.entity.DocumentVersion;
import com.filemind.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentCommentService {

    private final DocumentCommentRepository documentCommentRepository;
    private final DocumentAccessService documentAccessService;
    private final VersionService versionService;
    private final ActivityService activityService;

    @Transactional(readOnly = true)
    public List<DocumentCommentResponse> list(User user, String documentId) {
        documentAccessService.requireReadableDocument(user, documentId);
        return documentCommentRepository.findByDocumentIdOrderByCreatedAtDesc(documentId)
            .stream()
            .map(DocumentCommentResponse::from)
            .toList();
    }

    @Transactional
    public DocumentCommentResponse add(User user, String documentId, CreateDocumentCommentRequest request) {
        Document document = documentAccessService.requireReadableDocument(user, documentId);
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isBlank()) {
            throw new BadRequestException("Review note content is required.");
        }

        DocumentVersion version = resolveVersion(documentId, request.versionId());

        DocumentComment comment = new DocumentComment();
        comment.setDocument(document);
        comment.setVersion(version);
        comment.setAuthorName(user.getFullName());
        comment.setAuthorEmail(user.getEmail());
        comment.setContent(content);
        comment = documentCommentRepository.save(comment);

        String details = version == null
            ? "Added a review note"
            : "Added a review note to version " + version.getVersionNumber();
        activityService.log(ActivityAction.REVIEW_NOTE_ADDED, user, document, version, details);

        return DocumentCommentResponse.from(comment);
    }

    private DocumentVersion resolveVersion(String documentId, String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return null;
        }

        DocumentVersion version = versionService.getCompletedVersionEntity(versionId);
        if (!documentId.equals(version.getDocument().getId())) {
            throw new BadRequestException("That version does not belong to this document.");
        }
        return version;
    }
}
