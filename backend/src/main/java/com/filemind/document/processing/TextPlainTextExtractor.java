package com.filemind.document.processing;

import com.filemind.common.util.FileTypeUtils;
import com.filemind.common.util.TextContentSanitizer;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TextPlainTextExtractor implements DocumentTextExtractor {

    @Override
    public boolean supports(String fileName, String mediaType) {
        return "text/plain".equals(mediaType) || "txt".equals(FileTypeUtils.fileExtension(fileName));
    }

    @Override
    public ExtractionResult extract(Path path, int maxCharacters) throws IOException {
        StringBuilder builder = new StringBuilder(Math.min(maxCharacters, 16_384));
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1 && builder.length() < maxCharacters) {
                int remaining = maxCharacters - builder.length();
                builder.append(buffer, 0, Math.min(remaining, read));
            }
        }
        return new ExtractionResult(TextContentSanitizer.stripLeadingBom(builder.toString()), false);
    }
}
