package com.filemind.share.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record EnableShareLinkRequest(
    @Min(value = 1, message = "Link expiry must be at least 1 day")
    @Max(value = 365, message = "Link expiry must be 365 days or fewer")
    Integer expiresInDays
) {
}
