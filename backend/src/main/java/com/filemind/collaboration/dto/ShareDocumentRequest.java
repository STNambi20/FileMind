package com.filemind.collaboration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ShareDocumentRequest(
    @Email(message = "Please enter a valid email address")
    @NotBlank(message = "Collaborator email is required")
    String email,

    @NotBlank(message = "Permission is required")
    @Pattern(regexp = "EDITOR|VIEWER", message = "Permission must be EDITOR or VIEWER")
    String permission
) {
}
