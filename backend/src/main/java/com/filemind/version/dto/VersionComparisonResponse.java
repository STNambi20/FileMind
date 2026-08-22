package com.filemind.version.dto;

import java.util.List;

public record VersionComparisonResponse(
    VersionSummaryResponse baseVersion,
    VersionSummaryResponse targetVersion,
    boolean comparable,
    String summary,
    int sharedLineCount,
    int addedLineCount,
    int removedLineCount,
    List<String> addedPreview,
    List<String> removedPreview,
    List<String> metadataChanges
) {
}
