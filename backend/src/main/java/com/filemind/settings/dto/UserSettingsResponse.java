package com.filemind.settings.dto;

import com.filemind.user.dto.CurrentUserResponse;

public record UserSettingsResponse(
    CurrentUserResponse account,
    UserPreferencesResponse preferences
) {
}
