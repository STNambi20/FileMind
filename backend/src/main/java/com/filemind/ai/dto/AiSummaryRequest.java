package com.filemind.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiSummaryRequest(
    String text,
    @JsonProperty("max_sentences")
    int maxSentences
) {
}
