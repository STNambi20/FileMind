package com.filemind.common.util;

public final class TextContentSanitizer {

    private static final char UTF8_BOM = '\uFEFF';

    private TextContentSanitizer() {
    }

    public static String stripLeadingBom(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.charAt(0) == UTF8_BOM ? value.substring(1) : value;
    }
}
