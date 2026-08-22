package com.filemind.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountSettingsRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be 255 characters or fewer")
    String fullName,
    @Size(max = 1024, message = "Avatar URL must be 1024 characters or fewer")
    String avatarUrl
) {
}
