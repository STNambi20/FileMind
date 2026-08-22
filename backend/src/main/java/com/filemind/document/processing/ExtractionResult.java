package com.filemind.document.processing;

public record ExtractionResult(
    String text,
    boolean ocrApplied
) {
    public static ExtractionResult empty() {
        return new ExtractionResult(null, false);
    }
}
