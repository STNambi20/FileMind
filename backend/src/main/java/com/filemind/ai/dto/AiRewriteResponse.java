package com.filemind.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AiRewriteResponse(
    @JsonProperty("rewrittenText")
    @JsonAlias("rewritten_text")
    String rewrittenText,
    String mode,
    @JsonProperty("versionCreated")
    boolean versionCreated,
    @JsonProperty("versionId")
    String versionId,
    @JsonProperty("versionNumber")
    Integer versionNumber
) {
}
