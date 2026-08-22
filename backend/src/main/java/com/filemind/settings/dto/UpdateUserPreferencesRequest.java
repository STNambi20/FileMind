package com.filemind.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserPreferencesRequest(
    @NotBlank(message = "Summary length is required")
    String summaryLength,
    @NotBlank(message = "Rewrite mode is required")
    String rewriteMode,
    @NotNull(message = "Auto tag generation preference is required")
    Boolean autoGenerateTagsOnUpload,
    @NotNull(message = "Open latest version preference is required")
    Boolean openLatestVersionAutomatically,
    @NotNull(message = "Wrap text preference is required")
    Boolean wrapTextForTextFiles,
    @NotBlank(message = "Default open mode is required")
    String defaultOpenMode,
    @NotNull(message = "Auto retry preference is required")
    Boolean autoRetryFailedProcessing,
    @NotNull(message = "Keep failed uploads preference is required")
    Boolean keepFailedUploadsVisible,
    @NotBlank(message = "Upload behavior is required")
    String afterUploadBehavior,
    @NotNull(message = "Upload completed notification preference is required")
    Boolean notifyUploadCompleted,
    @NotNull(message = "Processing failed notification preference is required")
    Boolean notifyProcessingFailed,
    @NotNull(message = "Version created notification preference is required")
    Boolean notifyVersionCreated,
    @Min(value = 1, message = "Share expiry must be at least 1 day")
    @Max(value = 365, message = "Share expiry must be 365 days or fewer")
    Integer defaultShareExpiryDays,
    @NotBlank(message = "Default visibility is required")
    String defaultVisibility,
    @NotBlank(message = "Layout density is required")
    String layoutDensity,
    @NotNull(message = "Reduced motion preference is required")
    Boolean reducedMotion,
    @NotNull(message = "High contrast preference is required")
    Boolean highContrastMode
) {
}
