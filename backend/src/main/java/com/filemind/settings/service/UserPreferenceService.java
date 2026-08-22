package com.filemind.settings.service;

import com.filemind.common.exception.BadRequestException;
import com.filemind.settings.dto.UpdateAccountSettingsRequest;
import com.filemind.settings.dto.UpdateUserPreferencesRequest;
import com.filemind.settings.dto.UserPreferencesResponse;
import com.filemind.settings.dto.UserSettingsResponse;
import com.filemind.settings.entity.DefaultOpenMode;
import com.filemind.settings.entity.LayoutDensity;
import com.filemind.settings.entity.RewritePreference;
import com.filemind.settings.entity.ShareVisibility;
import com.filemind.settings.entity.SummaryPreference;
import com.filemind.settings.entity.UploadCompletionBehavior;
import com.filemind.settings.entity.UserPreference;
import com.filemind.settings.repository.UserPreferenceRepository;
import com.filemind.user.dto.CurrentUserResponse;
import com.filemind.user.entity.User;
import com.filemind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserSettingsResponse getSettings(User user) {
        UserPreference preference = getOrCreatePreference(user);
        return toResponse(user, preference);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserPreference getPreferences(User user) {
        return getOrCreatePreference(user);
    }

    @Transactional
    public UserSettingsResponse updatePreferences(User user, UpdateUserPreferencesRequest request) {
        User persistedUser = requirePersistedUser(user);
        UserPreference preference = getOrCreatePreference(persistedUser);
        preference.setSummaryPreference(parseEnum(SummaryPreference.class, request.summaryLength(), "summary length"));
        preference.setRewritePreference(parseEnum(RewritePreference.class, request.rewriteMode(), "rewrite mode"));
        preference.setAutoGenerateTagsOnUpload(request.autoGenerateTagsOnUpload());
        preference.setOpenLatestVersionAutomatically(request.openLatestVersionAutomatically());
        preference.setWrapTextForTextFiles(request.wrapTextForTextFiles());
        preference.setDefaultOpenMode(parseEnum(DefaultOpenMode.class, request.defaultOpenMode(), "default open mode"));
        preference.setAutoRetryFailedProcessing(request.autoRetryFailedProcessing());
        preference.setKeepFailedUploadsVisible(request.keepFailedUploadsVisible());
        preference.setUploadCompletionBehavior(parseEnum(UploadCompletionBehavior.class, request.afterUploadBehavior(), "upload behavior"));
        preference.setNotifyUploadCompleted(request.notifyUploadCompleted());
        preference.setNotifyProcessingFailed(request.notifyProcessingFailed());
        preference.setNotifyVersionCreated(request.notifyVersionCreated());
        preference.setDefaultShareExpiryDays(request.defaultShareExpiryDays());
        preference.setDefaultShareVisibility(parseEnum(ShareVisibility.class, request.defaultVisibility(), "default visibility"));
        preference.setLayoutDensity(parseEnum(LayoutDensity.class, request.layoutDensity(), "layout density"));
        preference.setReducedMotion(request.reducedMotion());
        preference.setHighContrastMode(request.highContrastMode());
        userPreferenceRepository.save(preference);
        return toResponse(persistedUser, preference);
    }

    @Transactional
    public UserSettingsResponse updateAccount(User user, UpdateAccountSettingsRequest request) {
        User persistedUser = requirePersistedUser(user);
        persistedUser.setFullName(request.fullName().trim());
        persistedUser.setAvatarUrl(normalizeAvatarUrl(request.avatarUrl()));
        User savedUser = userRepository.save(persistedUser);
        return toResponse(savedUser, getOrCreatePreference(savedUser));
    }

    private UserSettingsResponse toResponse(User user, UserPreference preference) {
        return new UserSettingsResponse(CurrentUserResponse.from(user), UserPreferencesResponse.from(preference));
    }

    private UserPreference getOrCreatePreference(User user) {
        return userPreferenceRepository.findByUserId(user.getId())
            .orElseGet(() -> userPreferenceRepository.save(buildDefaultPreference(user)));
    }

    private UserPreference buildDefaultPreference(User user) {
        UserPreference preference = new UserPreference();
        preference.setUser(user);
        return preference;
    }

    private User requirePersistedUser(User user) {
        return userRepository.findById(user.getId())
            .orElseThrow(() -> new BadRequestException("Your account is no longer available."));
    }

    private String normalizeAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        return avatarUrl.trim();
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Choose a " + label + ".");
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported " + label + ".");
        }
    }
}
