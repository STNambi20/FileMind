package com.filemind.collaboration.service;

import com.filemind.activity.entity.ActivityAction;
import com.filemind.activity.service.ActivityService;
import com.filemind.collaboration.dto.CollaboratorResponse;
import com.filemind.collaboration.dto.ShareDocumentRequest;
import com.filemind.collaboration.dto.UpdateCollaboratorPermissionRequest;
import com.filemind.collaboration.entity.DocumentAccess;
import com.filemind.collaboration.entity.DocumentPermission;
import com.filemind.collaboration.repository.DocumentAccessRepository;
import com.filemind.common.exception.BadRequestException;
import com.filemind.common.exception.ForbiddenOperationException;
import com.filemind.common.exception.ResourceNotFoundException;
import com.filemind.document.entity.Document;
import com.filemind.document.repository.DocumentRepository;
import com.filemind.user.entity.User;
import com.filemind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentAccessService {

    private final DocumentRepository documentRepository;
    private final DocumentAccessRepository documentAccessRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    @Transactional(readOnly = true)
    public List<Document> listAccessibleDocuments(User user) {
        return documentRepository.findAccessibleDocuments(user.getId());
    }

    @Transactional(readOnly = true)
    public Document requireReadableDocument(User user, String documentId) {
        Document document = getActiveDocument(documentId);
        resolveAccess(user, document);
        return document;
    }

    @Transactional(readOnly = true)
    public Document requireEditableDocument(User user, String documentId) {
        Document document = getActiveDocument(documentId);
        AccessContext access = resolveAccess(user, document);
        if (!access.canEdit()) {
            throw new ForbiddenOperationException("You do not have permission to modify this document");
        }
        return document;
    }

    @Transactional(readOnly = true)
    public Document requireOwnedDocument(User user, String documentId) {
        Document document = getActiveDocument(documentId);
        if (!document.getOwner().getId().equals(user.getId())) {
            throw new ForbiddenOperationException("Only the document owner can perform this action");
        }
        return document;
    }

    @Transactional(readOnly = true)
    public AccessContext getAccessContext(User user, Document document) {
        return resolveAccess(user, document);
    }

    @Transactional(readOnly = true)
    public List<CollaboratorResponse> listCollaborators(Document document) {
        return documentAccessRepository.findByDocumentIdOrderByCreatedAtAsc(document.getId())
            .stream()
            .map(CollaboratorResponse::from)
            .toList();
    }

    @Transactional
    public CollaboratorResponse share(User actor, String documentId, ShareDocumentRequest request) {
        Document document = requireOwnedDocument(actor, documentId);
        User collaborator = userRepository.findByEmailIgnoreCase(request.email().trim())
            .orElseThrow(() -> new BadRequestException("No registered user exists with that email address"));

        if (document.getOwner().getId().equals(collaborator.getId())) {
            throw new BadRequestException("The owner already has full access to this document");
        }

        DocumentPermission permission = parseSharedPermission(request.permission());
        if (permission == DocumentPermission.OWNER) {
            throw new BadRequestException("Shared collaborators cannot be assigned OWNER permission");
        }

        DocumentAccess access = documentAccessRepository.findByDocumentIdAndUserId(documentId, collaborator.getId())
            .orElseGet(DocumentAccess::new);
        access.setDocument(document);
        access.setUser(collaborator);
        access.setPermission(permission);
        access = documentAccessRepository.save(access);

        activityService.log(
            ActivityAction.SHARE_GRANTED,
            actor,
            document,
            "Shared document with " + collaborator.getEmail() + " as " + permission.name()
        );
        return CollaboratorResponse.from(access);
    }

    @Transactional
    public CollaboratorResponse updatePermission(User actor,
                                                 String documentId,
                                                 String collaboratorId,
                                                 UpdateCollaboratorPermissionRequest request) {
        Document document = requireOwnedDocument(actor, documentId);
        DocumentAccess access = getCollaboratorAccess(documentId, collaboratorId);
        DocumentPermission permission = parseSharedPermission(request.permission());
        if (permission == DocumentPermission.OWNER) {
            throw new BadRequestException("Shared collaborators cannot be assigned OWNER permission");
        }
        access.setPermission(permission);
        access = documentAccessRepository.save(access);

        activityService.log(
            ActivityAction.SHARE_UPDATED,
            actor,
            document,
            "Updated share permission for " + access.getUser().getEmail() + " to " + permission.name()
        );
        return CollaboratorResponse.from(access);
    }

    @Transactional
    public void revoke(User actor, String documentId, String collaboratorId) {
        Document document = requireOwnedDocument(actor, documentId);
        DocumentAccess access = getCollaboratorAccess(documentId, collaboratorId);
        String collaboratorEmail = access.getUser().getEmail();
        documentAccessRepository.delete(access);

        activityService.log(
            ActivityAction.SHARE_REVOKED,
            actor,
            document,
            "Removed shared access for " + collaboratorEmail
        );
    }

    @Transactional
    public void deleteAllForDocument(String documentId) {
        documentAccessRepository.deleteByDocumentId(documentId);
    }

    private DocumentAccess getCollaboratorAccess(String documentId, String collaboratorId) {
        DocumentAccess access = documentAccessRepository.findById(collaboratorId)
            .orElseThrow(() -> new ResourceNotFoundException("Collaborator not found"));
        if (!access.getDocument().getId().equals(documentId)) {
            throw new ResourceNotFoundException("Collaborator not found for this document");
        }
        return access;
    }

    private Document getActiveDocument(String documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private AccessContext resolveAccess(User user, Document document) {
        if (document.getOwner().getId().equals(user.getId())) {
            return new AccessContext(DocumentPermission.OWNER, true, true);
        }

        DocumentAccess access = documentAccessRepository.findByDocumentIdAndUserId(document.getId(), user.getId())
            .orElseThrow(() -> new ForbiddenOperationException("You do not have permission to access this document"));

        return new AccessContext(access.getPermission(), access.getPermission().canEdit(), false);
    }

    private DocumentPermission parseSharedPermission(String rawPermission) {
        try {
            return DocumentPermission.valueOf(rawPermission.trim().toUpperCase());
        } catch (Exception exception) {
            throw new BadRequestException("Permission must be EDITOR or VIEWER");
        }
    }

    public record AccessContext(DocumentPermission permission, boolean canEdit, boolean canManageAccess) {
    }
}
