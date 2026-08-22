package com.filemind.document.processing;

import java.nio.file.Path;

public interface DocumentTextExtractor {

    boolean supports(String fileName, String mediaType);

    ExtractionResult extract(Path path, int maxCharacters) throws Exception;
}
