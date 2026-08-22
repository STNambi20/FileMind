package com.filemind.auth.dto;

import com.filemind.user.dto.CurrentUserResponse;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresAt,
    CurrentUserResponse user
) {
}

