package com.filemind.document.processing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "filemind.document-processing")
public class DocumentProcessingProperties {

    private int maxExtractedCharacters = 200_000;
    private long maxInlineExtractionFileSizeBytes = 25L * 1024L * 1024L;
    private boolean ocrEnabled = true;
    private String tesseractCommand = "tesseract";
    private int ocrTimeoutSeconds = 12;
}
