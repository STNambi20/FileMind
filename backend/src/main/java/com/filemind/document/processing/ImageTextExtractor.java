package com.filemind.document.processing;

import com.filemind.common.util.FileTypeUtils;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;

@Component
public class ImageTextExtractor implements DocumentTextExtractor {

    private final OcrSupportService ocrSupportService;

    public ImageTextExtractor(OcrSupportService ocrSupportService) {
        this.ocrSupportService = ocrSupportService;
    }

    @Override
    public boolean supports(String fileName, String mediaType) {
        return FileTypeUtils.isImageMediaType(mediaType) || FileTypeUtils.isImageFile(fileName);
    }

    @Override
    public ExtractionResult extract(Path path, int maxCharacters) throws Exception {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            return ExtractionResult.empty();
        }

        StringBuilder builder = new StringBuilder(Math.min(maxCharacters, 2_048));
        builder.append("Image asset").append('\n');
        builder.append("Dimensions: ").append(image.getWidth()).append(" x ").append(image.getHeight()).append(" pixels").append('\n');
        builder.append("Orientation: ").append(orientationLabel(image)).append('\n');
        builder.append("Perceptual hash: ").append(perceptualHash(image)).append('\n');

        OcrSupportService.OcrOutcome ocrOutcome = ocrSupportService.extract(path, Math.max(0, maxCharacters - builder.length()));
        if (ocrOutcome.applied()) {
            builder.append('\n').append('\n').append("OCR text: ").append(ocrOutcome.text().trim());
        }

        String extracted = builder.toString().trim();
        String limited = extracted.length() <= maxCharacters ? extracted : extracted.substring(0, maxCharacters);
        return new ExtractionResult(limited, ocrOutcome.applied());
    }

    private String orientationLabel(BufferedImage image) {
        if (image.getWidth() == image.getHeight()) {
            return "square";
        }
        return image.getWidth() > image.getHeight() ? "landscape" : "portrait";
    }

    private String perceptualHash(BufferedImage image) {
        BufferedImage scaled = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image.getScaledInstance(8, 8, Image.SCALE_SMOOTH), 0, 0, null);
        graphics.dispose();

        long total = 0L;
        int[] values = new int[64];
        int index = 0;
        for (int y = 0; y < 8; y += 1) {
            for (int x = 0; x < 8; x += 1) {
                int value = scaled.getRaster().getSample(x, y, 0);
                values[index++] = value;
                total += value;
            }
        }

        int average = Math.toIntExact(total / values.length);
        long hash = 0L;
        for (int value : values) {
            hash <<= 1;
            if (value >= average) {
                hash |= 1L;
            }
        }
        return String.format(Locale.ROOT, "%016x", hash);
    }
}
