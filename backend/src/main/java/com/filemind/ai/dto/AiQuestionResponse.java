package com.filemind.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AiQuestionResponse(
    String question,
    String answer,
    @JsonProperty("evidenceSnippet")
    @JsonAlias("evidence_snippet")
    String evidenceSnippet,
    boolean answered
) {
}
