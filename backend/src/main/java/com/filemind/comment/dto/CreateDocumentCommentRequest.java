package com.filemind.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentCommentRequest(
    @NotBlank(message = "Review note content is required")
    @Size(max = 4000, message = "Review note must be 4000 characters or fewer")
    String content,
    String versionId
) {
}
