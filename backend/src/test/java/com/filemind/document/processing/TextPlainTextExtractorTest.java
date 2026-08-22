package com.filemind.document.processing;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TextPlainTextExtractorTest {

    private final TextPlainTextExtractor extractor = new TextPlainTextExtractor();

    @Test
    void stripsLeadingUtf8BomFromExtractedText() throws Exception {
        Path file = Files.createTempFile("filemind-text-bom", ".txt");
        try {
            Files.writeString(file, "\uFEFFHello FileMind", StandardCharsets.UTF_8);

            ExtractionResult result = extractor.extract(file, 200);

            assertThat(result.text()).isEqualTo("Hello FileMind");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
