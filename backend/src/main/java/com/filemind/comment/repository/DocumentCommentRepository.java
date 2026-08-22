package com.filemind.comment.repository;

import com.filemind.comment.entity.DocumentComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentCommentRepository extends JpaRepository<DocumentComment, String> {

    List<DocumentComment> findByDocumentIdOrderByCreatedAtDesc(String documentId);
}
