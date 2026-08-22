package com.filemind.version.mapper;

import com.filemind.common.util.JsonListCodec;
import com.filemind.common.util.TextContentSanitizer;
import com.filemind.document.model.ProcessingStatus;
import com.filemind.version.dto.VersionSummaryResponse;
import com.filemind.version.entity.DocumentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VersionResponseMapper {

    private final JsonListCodec jsonListCodec;

    public VersionSummaryResponse toSummary(DocumentVersion version) {
        return new VersionSummaryResponse(
            version.getId(),
            version.getVersionNumber(),
            version.getFileName(),
            version.getMediaType(),
            version.getOrigin().name(),
            version.getLogicalSize(),
            version.getActualStoredSize(),
            version.getDedupSavedSize(),
            version.getLogicalSize() == 0 ? 0D : (double) version.getDedupSavedSize() / version.getLogicalSize(),
            safeInt(version.getTotalChunkCount()),
            safeInt(version.getNewChunkCount()),
            safeInt(version.getReusedChunkCount()),
            version.getCreatedAt(),
            version.getCreatedBy().getFullName(),
            version.isCurrentVersion(),
            version.getNote(),
            TextContentSanitizer.stripLeadingBom(version.getSummary()),
            version.getTitleSuggestion(),
            jsonListCodec.read(version.getInsightsJson()),
            jsonListCodec.read(version.getHighlightsJson()),
            version.getReadabilityNote(),
            version.isOcrApplied(),
            jsonListCodec.read(version.getTagsJson()),
            version.getProcessingStatus().name(),
            version.getProcessingError(),
            version.getProcessingStatus() == ProcessingStatus.COMPLETED
        );
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
