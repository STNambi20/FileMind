package com.filemind.document.processing;

import com.filemind.common.util.FileTypeUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@Component
public class PdfTextExtractor implements DocumentTextExtractor {

    private static final int MAX_OCR_IMAGES = 6;

    private final OcrSupportService ocrSupportService;

    public PdfTextExtractor(OcrSupportService ocrSupportService) {
        this.ocrSupportService = ocrSupportService;
    }

    @Override
    public boolean supports(String fileName, String mediaType) {
        return "application/pdf".equals(mediaType) || "pdf".equals(FileTypeUtils.fileExtension(fileName));
    }

    @Override
    public ExtractionResult extract(Path path, int maxCharacters) throws Exception {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            String text = new PDFTextStripper().getText(document);
            int imageCount = countImages(document);
            StringBuilder builder = new StringBuilder(Math.min(maxCharacters, 16_384));
            builder.append("PDF pages: ").append(document.getNumberOfPages()).append('\n');
            builder.append("Embedded images: ").append(imageCount).append('\n');
            if (text != null && !text.isBlank()) {
                builder.append('\n').append(text.trim());
            }

            OcrAggregate ocrAggregate = extractImageText(document, Math.max(0, maxCharacters - builder.length()));
            if (ocrAggregate.applied()) {
                builder.append('\n').append('\n').append("OCR image text:").append('\n').append(ocrAggregate.text());
            }

            String extracted = builder.toString().trim();
            String limited = extracted.length() <= maxCharacters ? extracted : extracted.substring(0, maxCharacters);
            return new ExtractionResult(limited, ocrAggregate.applied());
        }
    }

    private int countImages(PDDocument document) throws Exception {
        int count = 0;
        Set<String> visitedForms = new HashSet<>();
        for (PDPage page : document.getPages()) {
            count += countImages(page.getResources(), visitedForms);
        }
        return count;
    }

    private int countImages(PDResources resources, Set<String> visitedForms) throws Exception {
        if (resources == null) {
            return 0;
        }

        int count = 0;
        for (var name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject) {
                count += 1;
            } else if (xObject instanceof PDFormXObject form) {
                String formName = form.getCOSObject().toString();
                if (visitedForms.add(formName)) {
                    count += countImages(form.getResources(), visitedForms);
                }
            }
        }
        return count;
    }

    private OcrAggregate extractImageText(PDDocument document, int maxCharacters) throws Exception {
        if (!ocrSupportService.isEnabled() || maxCharacters <= 0) {
            return OcrAggregate.none();
        }

        StringBuilder builder = new StringBuilder(Math.min(maxCharacters, 4_096));
        Set<String> visitedForms = new HashSet<>();
        Set<String> visitedImages = new HashSet<>();
        int processedImages = 0;

        for (PDPage page : document.getPages()) {
            processedImages = extractImageText(page.getResources(), visitedForms, visitedImages, builder, maxCharacters, processedImages);
            if (builder.length() >= maxCharacters || processedImages >= MAX_OCR_IMAGES) {
                break;
            }
        }

        String extracted = builder.toString().trim();
        if (extracted.isBlank()) {
            return OcrAggregate.none();
        }
        return new OcrAggregate(extracted, true);
    }

    private int extractImageText(PDResources resources,
                                 Set<String> visitedForms,
                                 Set<String> visitedImages,
                                 StringBuilder builder,
                                 int maxCharacters,
                                 int processedImages) throws Exception {
        if (resources == null || builder.length() >= maxCharacters || processedImages >= MAX_OCR_IMAGES) {
            return processedImages;
        }

        for (var name : resources.getXObjectNames()) {
            if (builder.length() >= maxCharacters || processedImages >= MAX_OCR_IMAGES) {
                break;
            }

            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject imageXObject) {
                String imageKey = imageXObject.getCOSObject().toString();
                if (!visitedImages.add(imageKey)) {
                    continue;
                }

                OcrSupportService.OcrOutcome outcome = ocrSupportService.extract(
                    imageXObject.getImage(),
                    "png",
                    Math.max(0, maxCharacters - builder.length())
                );
                processedImages += 1;
                if (outcome.applied()) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append("- ").append(outcome.text().trim());
                }
            } else if (xObject instanceof PDFormXObject form) {
                String formName = form.getCOSObject().toString();
                if (visitedForms.add(formName)) {
                    processedImages = extractImageText(form.getResources(), visitedForms, visitedImages, builder, maxCharacters, processedImages);
                }
            }
        }

        return processedImages;
    }

    private record OcrAggregate(String text, boolean applied) {
        private static OcrAggregate none() {
            return new OcrAggregate(null, false);
        }
    }
}
