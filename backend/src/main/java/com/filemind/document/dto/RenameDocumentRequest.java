package com.filemind.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameDocumentRequest(
    @NotBlank(message = "Document name is required")
    @Size(min = 2, max = 180, message = "Document name must be between 2 and 180 characters")
    String name
) {
}

