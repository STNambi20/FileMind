package com.filemind.common.dto;

public record HealthResponse(
    String status,
    String service,
    String message
) {
}
