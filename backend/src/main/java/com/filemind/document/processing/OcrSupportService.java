package com.filemind.document.processing;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class OcrSupportService {

    private static final int VERSION_CHECK_TIMEOUT_SECONDS = 4;

    private final DocumentProcessingProperties documentProcessingProperties;
    private volatile Boolean tesseractAvailable;

    public OcrSupportService(DocumentProcessingProperties documentProcessingProperties) {
        this.documentProcessingProperties = documentProcessingProperties;
    }

    public boolean isEnabled() {
        return documentProcessingProperties.isOcrEnabled() && hasWorkingTesseract();
    }

    public OcrOutcome extract(Path imagePath, int maxCharacters) {
        if (!isEnabled() || imagePath == null || maxCharacters <= 0) {
            return OcrOutcome.none();
        }

        Process process = null;
        try {
            process = new ProcessBuilder(
                documentProcessingProperties.getTesseractCommand(),
                imagePath.toString(),
                "stdout",
                "--psm",
                "6"
            ).redirectErrorStream(true).start();

            boolean finished = process.waitFor(documentProcessingProperties.getOcrTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return OcrOutcome.none();
            }
            if (process.exitValue() != 0) {
                return OcrOutcome.none();
            }

            try (InputStream inputStream = process.getInputStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                inputStream.transferTo(outputStream);
                String detected = outputStream.toString(StandardCharsets.UTF_8).trim();
                if (detected.isBlank()) {
                    return OcrOutcome.none();
                }
                return new OcrOutcome(
                    detected.length() <= maxCharacters ? detected : detected.substring(0, maxCharacters),
                    true
                );
            }
        } catch (Exception exception) {
            return OcrOutcome.none();
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    public OcrOutcome extract(byte[] imageBytes, String fileExtension, int maxCharacters) {
        if (!isEnabled() || imageBytes == null || imageBytes.length == 0 || maxCharacters <= 0) {
            return OcrOutcome.none();
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("filemind-ocr-", resolveSuffix(fileExtension));
            Files.write(tempFile, imageBytes);
            return extract(tempFile, maxCharacters);
        } catch (Exception exception) {
            return OcrOutcome.none();
        } finally {
            deleteQuietly(tempFile);
        }
    }

    public OcrOutcome extract(BufferedImage image, String format, int maxCharacters) {
        if (!isEnabled() || image == null || maxCharacters <= 0) {
            return OcrOutcome.none();
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            String normalizedFormat = normalizeFormat(format);
            if (!ImageIO.write(image, normalizedFormat, outputStream)) {
                return OcrOutcome.none();
            }
            return extract(outputStream.toByteArray(), normalizedFormat, maxCharacters);
        } catch (Exception exception) {
            return OcrOutcome.none();
        }
    }

    private boolean hasWorkingTesseract() {
        Boolean cached = tesseractAvailable;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (tesseractAvailable != null) {
                return tesseractAvailable;
            }

            Process process = null;
            boolean available = false;
            try {
                process = new ProcessBuilder(documentProcessingProperties.getTesseractCommand(), "--version")
                    .redirectErrorStream(true)
                    .start();
                boolean finished = process.waitFor(VERSION_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                available = finished && process.exitValue() == 0;
            } catch (Exception ignored) {
                available = false;
            } finally {
                if (process != null) {
                    process.destroyForcibly();
                }
            }

            tesseractAvailable = available;
            return available;
        }
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "png";
        }
        String normalized = format.toLowerCase(Locale.ROOT).replace(".", "");
        return switch (normalized) {
            case "jpg", "jpeg" -> "jpg";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            default -> "png";
        };
    }

    private String resolveSuffix(String fileExtension) {
        return "." + normalizeFormat(fileExtension);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    public record OcrOutcome(String text, boolean applied) {
        public static OcrOutcome none() {
            return new OcrOutcome(null, false);
        }
    }
}
