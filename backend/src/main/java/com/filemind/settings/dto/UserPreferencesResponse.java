package com.filemind.settings.dto;

import com.filemind.settings.entity.UserPreference;

public record UserPreferencesResponse(
    String summaryLength,
    String rewriteMode,
    boolean autoGenerateTagsOnUpload,
    boolean openLatestVersionAutomatically,
    boolean wrapTextForTextFiles,
    String defaultOpenMode,
    boolean autoRetryFailedProcessing,
    boolean keepFailedUploadsVisible,
    String afterUploadBehavior,
    boolean notifyUploadCompleted,
    boolean notifyProcessingFailed,
    boolean notifyVersionCreated,
    Integer defaultShareExpiryDays,
    String defaultVisibility,
    String layoutDensity,
    boolean reducedMotion,
    boolean highContrastMode
) {
    public static UserPreferencesResponse from(UserPreference preference) {
        return new UserPreferencesResponse(
            preference.getSummaryPreference().name(),
            preference.getRewritePreference().name(),
            preference.isAutoGenerateTagsOnUpload(),
            preference.isOpenLatestVersionAutomatically(),
            preference.isWrapTextForTextFiles(),
            preference.getDefaultOpenMode().name(),
            preference.isAutoRetryFailedProcessing(),
            preference.isKeepFailedUploadsVisible(),
            preference.getUploadCompletionBehavior().name(),
            preference.isNotifyUploadCompleted(),
            preference.isNotifyProcessingFailed(),
            preference.isNotifyVersionCreated(),
            preference.getDefaultShareExpiryDays(),
            preference.getDefaultShareVisibility().name(),
            preference.getLayoutDensity().name(),
            preference.isReducedMotion(),
            preference.isHighContrastMode()
        );
    }
}
