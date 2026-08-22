package com.filemind.document.dto;

import java.util.List;

public record DocumentInsightMetadataResponse(
    List<String> dates,
    List<String> names,
    List<String> keywords
) {
}
