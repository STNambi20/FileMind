package com.filemind.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateCollaboratorPermissionRequest(
    @NotBlank(message = "Permission is required")
    @Pattern(regexp = "EDITOR|VIEWER", message = "Permission must be EDITOR or VIEWER")
    String permission
) {
}
