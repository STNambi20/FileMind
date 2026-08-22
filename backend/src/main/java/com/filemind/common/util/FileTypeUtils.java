package com.filemind.common.util;

public final class FileTypeUtils {

    private static final java.util.Set<String> IMAGE_EXTENSIONS = java.util.Set.of("png", "jpg", "jpeg", "gif", "bmp");

    private FileTypeUtils() {
    }

    public static String fileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    public static String mediaTypeFor(String fileName, String providedMediaType) {
        if (providedMediaType != null && !providedMediaType.isBlank() && !"application/octet-stream".equals(providedMediaType)) {
            return providedMediaType;
        }

        return switch (fileExtension(fileName)) {
            case "txt" -> "text/plain";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    public static boolean isSupportedDocument(String fileName) {
        String extension = fileExtension(fileName);
        return switch (extension) {
            case "txt", "pdf", "docx" -> true;
            default -> IMAGE_EXTENSIONS.contains(extension);
        };
    }

    public static boolean isImageFile(String fileName) {
        return IMAGE_EXTENSIONS.contains(fileExtension(fileName));
    }

    public static boolean isImageMediaType(String mediaType) {
        return mediaType != null && mediaType.toLowerCase().startsWith("image/");
    }
}
