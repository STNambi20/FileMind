package com.filemind.document.processing;

import com.filemind.common.util.FileTypeUtils;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DocxTextExtractor implements DocumentTextExtractor {

    private static final int MAX_OCR_IMAGES = 6;

    private final OcrSupportService ocrSupportService;

    public DocxTextExtractor(OcrSupportService ocrSupportService) {
        this.ocrSupportService = ocrSupportService;
    }

    @Override
    public boolean supports(String fileName, String mediaType) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mediaType)
            || "docx".equals(FileTypeUtils.fileExtension(fileName));
    }

    @Override
    public ExtractionResult extract(Path path, int maxCharacters) throws Exception {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            StringBuilder builder = new StringBuilder(Math.min(maxCharacters, 16_384));
            builder.append("DOCX paragraphs: ").append(document.getParagraphs().size()).append('\n');
            builder.append("Embedded images: ").append(document.getAllPictures().size()).append('\n');
            if (text != null && !text.isBlank()) {
                builder.append('\n').append(text.trim());
            }

            boolean ocrApplied = false;
            if (ocrSupportService.isEnabled() && builder.length() < maxCharacters) {
                StringBuilder ocrBuilder = new StringBuilder(Math.min(maxCharacters - builder.length(), 4_096));
                int processed = 0;
                for (var picture : document.getAllPictures()) {
                    if (processed >= MAX_OCR_IMAGES || ocrBuilder.length() >= maxCharacters - builder.length()) {
                        break;
                    }

                    OcrSupportService.OcrOutcome outcome = ocrSupportService.extract(
                        picture.getData(),
                        picture.suggestFileExtension(),
                        Math.max(0, maxCharacters - builder.length() - ocrBuilder.length())
                    );
                    processed += 1;
                    if (outcome.applied()) {
                        if (ocrBuilder.length() > 0) {
                            ocrBuilder.append('\n');
                        }
                        ocrBuilder.append("- ").append(outcome.text().trim());
                        ocrApplied = true;
                    }
                }

                if (ocrBuilder.length() > 0) {
                    builder.append('\n').append('\n').append("OCR image text:").append('\n').append(ocrBuilder);
                }
            }

            String extracted = builder.toString().trim();
            String limited = extracted.length() <= maxCharacters ? extracted : extracted.substring(0, maxCharacters);
            return new ExtractionResult(limited, ocrApplied);
        }
    }
}
