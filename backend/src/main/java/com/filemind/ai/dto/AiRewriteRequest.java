package com.filemind.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AiRewriteRequest(
    @NotBlank(message = "Rewrite mode is required")
    @Pattern(regexp = "^(professional|formal|simplify|shorten|key-points|readability)$", message = "Rewrite mode must be professional, formal, simplify, shorten, key-points, or readability")
    String mode,
    boolean applyAsNewVersion
) {
}
