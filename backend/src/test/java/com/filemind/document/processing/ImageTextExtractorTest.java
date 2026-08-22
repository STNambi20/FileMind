package com.filemind.document.processing;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTextExtractorTest {

    @Test
    void extractsDeterministicImageMetadata() throws Exception {
        DocumentProcessingProperties properties = new DocumentProcessingProperties();
        properties.setOcrEnabled(false);
        ImageTextExtractor extractor = new ImageTextExtractor(new OcrSupportService(properties));
        Path imagePath = Files.createTempFile("filemind-image-meta-", ".png");

        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(12, 28, 64));
        graphics.fillRect(0, 0, 320, 180);
        graphics.setColor(new Color(245, 245, 245));
        graphics.fillRect(24, 36, 160, 48);
        graphics.dispose();
        ImageIO.write(image, "png", imagePath.toFile());

        ExtractionResult extracted = extractor.extract(imagePath, 4096);

        assertThat(extracted.text()).contains("Image asset");
        assertThat(extracted.text()).contains("Dimensions: 320 x 180 pixels");
        assertThat(extracted.text()).contains("Orientation: landscape");
        assertThat(extracted.text()).contains("Perceptual hash:");
        assertThat(extracted.ocrApplied()).isFalse();
    }
}
