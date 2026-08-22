package com.filemind.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiSummaryResponse(
    String summary,
    @JsonProperty("titleSuggestion")
    @JsonAlias("title_suggestion")
    String titleSuggestion,
    @JsonProperty("keyInsights")
    @JsonAlias("key_insights")
    List<String> keyInsights,
    @JsonProperty("bulletHighlights")
    @JsonAlias("bullet_highlights")
    List<String> bulletHighlights,
    @JsonProperty("readabilityNote")
    @JsonAlias("readability_note")
    String readabilityNote
) {
}
