package com.filemind.document;

import com.filemind.chunk.dto.StorageDiagnosticsResponse;
import com.filemind.chunk.model.ManifestPart;
import com.filemind.chunk.model.ManifestStorageMode;
import com.filemind.chunk.model.StoredManifest;
import com.filemind.chunk.repository.StoredChunkRepository;
import com.filemind.chunk.service.StorageDiagnosticsService;
import com.filemind.chunk.storage.StorageProvider;
import com.filemind.document.dto.DocumentDetailsResponse;
import com.filemind.document.dto.UploadDocumentResponse;
import com.filemind.document.service.DocumentService;
import com.filemind.support.DocumentProcessingTestSupport;
import com.filemind.user.entity.User;
import com.filemind.user.entity.UserRole;
import com.filemind.user.repository.UserRepository;
import com.filemind.version.entity.DocumentVersion;
import com.filemind.version.repository.DocumentVersionRepository;
import com.filemind.version.service.VersionService;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:docx-dedup-flow;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/docx-dedup",
    "filemind.storage.uploads=target/test-storage/docx-dedup/uploads",
    "filemind.storage.chunks=target/test-storage/docx-dedup/chunks",
    "filemind.storage.manifests=target/test-storage/docx-dedup/manifests"
})
class DocxFormatAwareDedupIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredChunkRepository storedChunkRepository;

    @Autowired
    private StorageProvider storageProvider;

    @Autowired
    private StorageDiagnosticsService storageDiagnosticsService;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/docx-dedup");
        if (Files.exists(root)) {
            try (var stream = Files.walk(root)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }

        Files.createDirectories(root.resolve("uploads"));
        Files.createDirectories(root.resolve("chunks"));
        Files.createDirectories(root.resolve("manifests"));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE activity_events");
        jdbcTemplate.execute("TRUNCATE TABLE public_share_links");
        jdbcTemplate.execute("TRUNCATE TABLE document_access");
        jdbcTemplate.execute("TRUNCATE TABLE document_versions");
        jdbcTemplate.execute("TRUNCATE TABLE documents");
        jdbcTemplate.execute("TRUNCATE TABLE stored_chunks");
        jdbcTemplate.execute("TRUNCATE TABLE users");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    void reusesInternalDocxPartsAcrossVersionsAndDocuments() throws Exception {
        User user = createUser("docx-owner@example.com");
        byte[] sharedImage = createNoisePng(1400, 1400);
        Path baseDocx = writeDocxFile("Architecture Review", "Prepared for storage optimization review.", sharedImage, 140);
        Path revisedDocx = writeDocxFile("Architecture Review", "Prepared for storage optimization and compliance review.", sharedImage, 140);

        UploadDocumentResponse firstUpload = documentService.uploadNewDocument(
            user,
            new PathMultipartFile(baseDocx, "proposal.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        );
        DocumentProcessingTestSupport.waitForTerminalStatus(documentService, user, firstUpload.documentId());

        long initialStoredBytes = storedChunkRepository.totalStoredBytes();
        UploadDocumentResponse secondUpload = documentService.uploadNewVersion(
            user,
            firstUpload.documentId(),
            new PathMultipartFile(revisedDocx, "proposal-v2.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        );

        assertThat(secondUpload.versionCreated()).isTrue();
        DocumentProcessingTestSupport.waitForTerminalStatus(documentService, user, firstUpload.documentId());
        long afterVersionStoredBytes = storedChunkRepository.totalStoredBytes();
        assertThat(afterVersionStoredBytes - initialStoredBytes).isLessThan(Files.size(revisedDocx) / 2);

        UploadDocumentResponse thirdUpload = documentService.uploadNewDocument(
            user,
            new PathMultipartFile(revisedDocx, "proposal-copy.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        );
        DocumentProcessingTestSupport.waitForTerminalStatus(documentService, user, thirdUpload.documentId());

        StorageDiagnosticsResponse diagnostics = storageDiagnosticsService.diagnostics();
        assertThat(diagnostics.savedBytes()).isGreaterThan(2_000_000L);
        assertThat(diagnostics.reusedChunks()).isGreaterThan(0L);
        assertThat(diagnostics.actualStoredBytes()).isLessThan(diagnostics.logicalSizeBytes());

        DocumentDetailsResponse details = documentService.getDetails(user, firstUpload.documentId());
        assertThat(details.totalVersions()).isEqualTo(2);
        assertThat(details.currentVersion().versionNumber()).isEqualTo(2);
        assertThat(details.currentVersion().reusedChunkCount()).isGreaterThan(0);
        assertThat(details.currentVersion().actualStoredSize()).isLessThan(details.currentVersion().logicalSize());

        DocumentVersion currentVersion = documentVersionRepository.findById(secondUpload.versionId()).orElseThrow();
        StoredManifest manifest = storageProvider.readManifest(currentVersion.getManifestPath());
        assertThat(manifest.storageMode()).isEqualTo(ManifestStorageMode.DOCX_PACKAGE);
        assertThat(manifest.parts()).extracting(ManifestPart::name).contains("word/document.xml");
        assertThat(manifest.parts()).anyMatch(part -> part.name().startsWith("word/media/"));
        assertThat(manifest.parts()).anyMatch(part -> part.name().equals("word/document.xml") && part.chunked());

        byte[] downloaded = versionService.download(secondUpload.versionId());
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(downloaded));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            assertThat(text).contains("Prepared for storage optimization and compliance review.");
            assertThat(document.getAllPictures()).isNotEmpty();
        }

        DocumentDetailsResponse copiedDocument = documentService.getDetails(user, thirdUpload.documentId());
        assertThat(copiedDocument.storage().logicalSize()).isGreaterThan(0L);
    }

    private User createUser(String email) {
        User user = new User();
        user.setFullName("DOCX Owner");
        user.setEmail(email);
        user.setPasswordHash("encoded");
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    private Path writeDocxFile(String title, String variantLine, byte[] sharedImage, int paragraphs) throws Exception {
        Path file = Files.createTempFile("filemind-docx-", ".docx");
        try (XWPFDocument document = new XWPFDocument()) {
            var titleParagraph = document.createParagraph();
            var titleRun = titleParagraph.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText(title);

            for (int index = 0; index < paragraphs; index += 1) {
                var paragraph = document.createParagraph();
                var run = paragraph.createRun();
                if (index == 12) {
                    run.setText(variantLine);
                } else {
                    run.setText("FileMind keeps document versions reusable across internal DOCX parts while preserving a valid Office package.");
                }
            }

            var imageParagraph = document.createParagraph();
            var imageRun = imageParagraph.createRun();
            try (InputStream inputStream = new ByteArrayInputStream(sharedImage)) {
                imageRun.addPicture(inputStream, XWPFDocument.PICTURE_TYPE_PNG, "shared-diagram.png", Units.toEMU(540), Units.toEMU(540));
            }

            try (var outputStream = Files.newOutputStream(file)) {
                document.write(outputStream);
            }
        }

        file.toFile().deleteOnExit();
        return file;
    }

    private byte[] createNoisePng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(20260323L);
        for (int y = 0; y < height; y += 1) {
            for (int x = 0; x < width; x += 1) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private static class PathMultipartFile implements MultipartFile {

        private final Path path;
        private final String originalFilename;
        private final String contentType;

        private PathMultipartFile(Path path, String originalFilename, String contentType) {
            this.path = path;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            try {
                return Files.size(path) == 0;
            } catch (IOException exception) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read file size", exception);
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(Path destination) throws IOException, IllegalStateException {
            Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void transferTo(java.io.File destination) throws IOException, IllegalStateException {
            Files.copy(path, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
