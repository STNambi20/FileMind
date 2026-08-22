package com.filemind.document;

import com.filemind.chunk.repository.StoredChunkRepository;
import com.filemind.chunk.util.ChunkHashUtils;
import com.filemind.document.dto.DocumentDetailsResponse;
import com.filemind.document.dto.UploadDocumentResponse;
import com.filemind.document.service.DocumentService;
import com.filemind.support.DocumentProcessingTestSupport;
import com.filemind.user.entity.User;
import com.filemind.user.entity.UserRole;
import com.filemind.user.repository.UserRepository;
import com.filemind.version.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:large-upload-flow;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/large-upload-flow",
    "filemind.storage.uploads=target/test-storage/large-upload-flow/uploads",
    "filemind.storage.chunks=target/test-storage/large-upload-flow/chunks",
    "filemind.storage.manifests=target/test-storage/large-upload-flow/manifests"
})
class LargeFileUploadIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VersionService versionService;

    @Autowired
    private StoredChunkRepository storedChunkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanStorage() throws IOException {
        Path root = Path.of("target/test-storage/large-upload-flow");
        if (Files.exists(root)) {
            try (var stream = Files.walk(root)) {
                stream.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
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

    @ParameterizedTest
    @ValueSource(ints = {10, 50, 100})
    void uploadsLargeFilesWithoutBreakingReconstruction(int sizeInMegabytes) throws Exception {
        User user = createUser("large-" + sizeInMegabytes + "@example.com");
        Path source = writeLargeTextFile(sizeInMegabytes, "Prepared for storage verification.");

        UploadDocumentResponse response = documentService.uploadNewDocument(
            user,
            new PathMultipartFile(source, "large-" + sizeInMegabytes + ".txt", "text/plain")
        );

        assertThat(response.versionCreated()).isTrue();
        assertThat(response.versionNumber()).isEqualTo(1);
        DocumentProcessingTestSupport.waitForTerminalStatus(documentService, user, response.documentId());

        byte[] downloaded = versionService.download(response.versionId());
        assertThat(ChunkHashUtils.sha256(new ByteArrayInputStream(downloaded))).isEqualTo(ChunkHashUtils.sha256(source));
        assertThat(storedChunkRepository.totalStoredBytes()).isLessThan(Files.size(source));
    }

    @Test
    void reusesChunksForLargeEditedVersionsAndKeepsMetricsConsistent() throws Exception {
        User user = createUser("large-edits@example.com");
        Path original = writeLargeTextFile(50, "Prepared for architecture review.");
        Path revised = writeLargeTextFile(50, "Prepared for architecture and compliance review.");

        UploadDocumentResponse firstUpload = documentService.uploadNewDocument(
            user,
            new PathMultipartFile(original, "large-base.txt", "text/plain")
        );
        DocumentProcessingTestSupport.waitForTerminalStatus(documentService, user, firstUpload.documentId());

        long initialStoredBytes = storedChunkRepository.totalStoredBytes();

        UploadDocumentResponse secondUpload = documentService.uploadNewVersion(
            user,
            firstUpload.documentId(),
            new PathMultipartFile(revised, "large-base-v2.txt", "text/plain")
        );

        assertThat(secondUpload.versionCreated()).isTrue();
        assertThat(secondUpload.versionNumber()).isEqualTo(2);
        DocumentProcessingTestSupport.waitForTerminalStatus(documentService, user, firstUpload.documentId());

        long updatedStoredBytes = storedChunkRepository.totalStoredBytes();
        assertThat(updatedStoredBytes).isLessThan(Files.size(original) + Files.size(revised));
        assertThat(updatedStoredBytes - initialStoredBytes).isLessThan(Files.size(revised) / 2);

        DocumentDetailsResponse details = documentService.getDetails(user, firstUpload.documentId());
        assertThat(details.totalVersions()).isEqualTo(2);
        assertThat(details.currentVersion().versionNumber()).isEqualTo(2);
        assertThat(details.storage().actualStoredSize()).isEqualTo(updatedStoredBytes);
        assertThat(details.storage().logicalSize()).isEqualTo(Files.size(original) + Files.size(revised));
        assertThat(details.storage().dedupSavedSize()).isGreaterThan(0L);
        assertThat(details.storage().reusedChunkReferences()).isGreaterThan(0L);

        byte[] downloaded = versionService.download(secondUpload.versionId());
        assertThat(ChunkHashUtils.sha256(new ByteArrayInputStream(downloaded))).isEqualTo(ChunkHashUtils.sha256(revised));
    }

    @Test
    void acceptsFilesAboveThePreviousFixedLimitWhenStreamingIsUsed() throws Exception {
        User user = createUser("too-large@example.com");
        Path source = writeLargeTextFile(1, "Prepared for size validation.");

        UploadDocumentResponse response = documentService.uploadNewDocument(
            user,
            new DeclaredSizeMultipartFile(source, "too-large.txt", "text/plain", 101L * 1024L * 1024L)
        );

        assertThat(response.versionCreated()).isTrue();
        DocumentProcessingTestSupport.waitForTerminalStatus(documentService, user, response.documentId());
    }

    private User createUser(String email) {
        User user = new User();
        user.setFullName("Large Upload User");
        user.setEmail(email);
        user.setPasswordHash("encoded");
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    private Path writeLargeTextFile(int sizeInMegabytes, String variantLine) throws IOException {
        Path file = Files.createTempFile("filemind-large-", ".txt");
        long targetBytes = sizeInMegabytes * 1024L * 1024L;
        byte[] lead = "FileMind reuses unchanged chunks to keep storage growth under control while preserving exact downloads.\n".getBytes();
        byte[] repeated = "This section is intentionally repeated so deduplication can reuse stable content across large uploads.\n".getBytes();
        byte[] variant = (variantLine + "\n").getBytes();
        long written = 0L;
        int lineIndex = 0;

        try (var outputStream = Files.newOutputStream(file)) {
            while (written < targetBytes) {
                byte[] next = lineIndex == 1024 ? variant : (lineIndex % 16 == 0 ? lead : repeated);
                int remaining = (int) Math.min(next.length, targetBytes - written);
                outputStream.write(next, 0, remaining);
                written += remaining;
                lineIndex += 1;
            }
        }

        file.toFile().deleteOnExit();
        return file;
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
                throw new IllegalStateException("Unable to read staged file size", exception);
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

    private static final class DeclaredSizeMultipartFile extends PathMultipartFile {

        private final long declaredSize;

        private DeclaredSizeMultipartFile(Path path, String originalFilename, String contentType, long declaredSize) {
            super(path, originalFilename, contentType);
            this.declaredSize = declaredSize;
        }

        @Override
        public long getSize() {
            return declaredSize;
        }
    }
}
