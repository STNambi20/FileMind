package com.filemind.share.dto;

import java.time.Instant;

public record ShareLinkResponse(
    boolean enabled,
    String token,
    String url,
    Instant expiresAt,
    boolean expired
) {
}
