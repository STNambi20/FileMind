package com.filemind.document.processing;

import com.filemind.common.util.FileTypeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TextExtractionService {

    private final List<DocumentTextExtractor> extractors;
    private final DocumentProcessingProperties documentProcessingProperties;

    public ExtractionResult extract(String fileName, String mediaType, Path filePath) {
        try {
            if (!shouldExtract(fileName, mediaType, filePath)) {
                return ExtractionResult.empty();
            }
        } catch (Exception exception) {
            return ExtractionResult.empty();
        }

        return extractors.stream()
            .filter(extractor -> extractor.supports(fileName, mediaType))
            .findFirst()
            .map(extractor -> {
                try {
                    return extractor.extract(filePath, documentProcessingProperties.getMaxExtractedCharacters());
                } catch (Exception exception) {
                    return ExtractionResult.empty();
                }
            })
            .orElse(ExtractionResult.empty());
    }

    private boolean shouldExtract(String fileName, String mediaType, Path filePath) throws Exception {
        if ("text/plain".equals(mediaType) || (fileName != null && fileName.toLowerCase().endsWith(".txt"))) {
            return true;
        }
        if (FileTypeUtils.isImageMediaType(mediaType) || FileTypeUtils.isImageFile(fileName)) {
            return Files.size(filePath) <= documentProcessingProperties.getMaxInlineExtractionFileSizeBytes();
        }
        return Files.size(filePath) <= documentProcessingProperties.getMaxInlineExtractionFileSizeBytes();
    }
}
