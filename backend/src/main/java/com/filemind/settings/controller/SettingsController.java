package com.filemind.settings.controller;

import com.filemind.common.response.ApiResponse;
import com.filemind.common.security.AuthenticatedUserService;
import com.filemind.settings.dto.UpdateAccountSettingsRequest;
import com.filemind.settings.dto.UpdateUserPreferencesRequest;
import com.filemind.settings.dto.UserSettingsResponse;
import com.filemind.settings.service.UserPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UserPreferenceService userPreferenceService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public ApiResponse<UserSettingsResponse> getSettings() {
        return ApiResponse.success(
            "Settings ready",
            userPreferenceService.getSettings(authenticatedUserService.getCurrentUser())
        );
    }

    @PatchMapping("/preferences")
    public ApiResponse<UserSettingsResponse> updatePreferences(@Valid @RequestBody UpdateUserPreferencesRequest request) {
        return ApiResponse.success(
            "Preferences updated",
            userPreferenceService.updatePreferences(authenticatedUserService.getCurrentUser(), request)
        );
    }

    @PatchMapping("/account")
    public ApiResponse<UserSettingsResponse> updateAccount(@Valid @RequestBody UpdateAccountSettingsRequest request) {
        return ApiResponse.success(
            "Account updated",
            userPreferenceService.updateAccount(authenticatedUserService.getCurrentUser(), request)
        );
    }
}
