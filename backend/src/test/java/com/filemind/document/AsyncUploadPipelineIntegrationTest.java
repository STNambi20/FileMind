package com.filemind.document;

import com.filemind.document.dto.DocumentDetailsResponse;
import com.filemind.document.dto.DocumentProcessingStatusResponse;
import com.filemind.document.dto.UploadDocumentResponse;
import com.filemind.document.entity.Document;
import com.filemind.document.model.ProcessingStatus;
import com.filemind.document.repository.DocumentRepository;
import com.filemind.document.service.DocumentService;
import com.filemind.support.DocumentProcessingTestSupport;
import com.filemind.user.entity.User;
import com.filemind.user.entity.UserRole;
import com.filemind.user.repository.UserRepository;
import com.filemind.version.entity.DocumentVersion;
import com.filemind.version.entity.VersionOrigin;
import com.filemind.version.repository.DocumentVersionRepository;
import com.filemind.version.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:async-upload-flow;DB_CLOSE_DELAY=-1",
    "filemind.storage.root=target/test-storage/async-upload-flow",
    "filemind.storage.uploads=target/test-storage/async-upload-flow/uploads",
    "filemind.storage.chunks=target/test-storage/async-upload-flow/chunks",
    "filemind.storage.manifests=target/test-storage/async-upload-flow/manifests"
})
class AsyncUploadPipelineIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanStorageAndDatabase() throws IOException {
        Path root = Path.of("target/test-storage/async-upload-flow");
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

    @Test
    void uploadReturnsImmediatelyAndDocumentBecomesReadyAfterBackgroundProcessing() throws Exception {
        User user = createUser("async-flow@example.com");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "async-flow.txt",
            "text/plain",
            "Async upload pipeline verification.".getBytes()
        );

        UploadDocumentResponse response = documentService.uploadNewDocument(user, file);

        assertThat(response.status()).isEqualTo(ProcessingStatus.UPLOADED.name());
        assertThat(response.readyForViewing()).isFalse();
        assertThat(response.documentId()).isNotBlank();
        assertThat(response.versionId()).isNotBlank();

        DocumentProcessingStatusResponse finalStatus =
            DocumentProcessingTestSupport.waitForTerminalStatus(documentService, user, response.documentId());

        assertThat(finalStatus.status()).isEqualTo(ProcessingStatus.COMPLETED.name());
        assertThat(finalStatus.readyForViewing()).isTrue();
        assertThat(finalStatus.currentVersionId()).isEqualTo(response.versionId());

        DocumentDetailsResponse details = documentService.getDetails(user, response.documentId());
        assertThat(details.readyForViewing()).isTrue();
        assertThat(details.processingStatus()).isEqualTo(ProcessingStatus.COMPLETED.name());
        assertThat(details.currentVersion()).isNotNull();
        assertThat(details.currentVersion().processingStatus()).isEqualTo(ProcessingStatus.COMPLETED.name());
    }

    @Test
    void marksQueuedUploadAsFailedWhenTheStagedFileDisappears() throws Exception {
        User user = createUser("async-failure@example.com");
        Document document = new Document();
        document.setName("failure-case.txt");
        document.setOwner(user);
        document.updateProcessingState(ProcessingStatus.UPLOADED, "Upload received. Processing has started.");
        document = documentRepository.save(document);

        Path stagedFile = Files.createTempFile("filemind-missing-stage-", ".txt");
        Files.writeString(stagedFile, "This file will be deleted before processing.");
        String contentHash = com.filemind.chunk.util.ChunkHashUtils.sha256(stagedFile);

        DocumentVersion queuedVersion = versionService.queueUpload(
            document,
            user,
            stagedFile,
            Files.size(stagedFile),
            contentHash,
            "failure-case.txt",
            "text/plain",
            "Failure simulation",
            VersionOrigin.UPLOAD
        );

        Files.deleteIfExists(stagedFile);
        versionService.processQueuedUpload(queuedVersion.getId());

        Document refreshedDocument = documentRepository.findById(document.getId()).orElseThrow();
        DocumentVersion failedVersion = documentVersionRepository.findById(queuedVersion.getId()).orElseThrow();

        assertThat(refreshedDocument.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(refreshedDocument.getProcessingMessage()).contains("staged upload file");
        assertThat(failedVersion.getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(failedVersion.getProcessingError()).contains("staged upload file");
        assertThat(failedVersion.isCurrentVersion()).isFalse();
        assertThat(failedVersion.getManifestPath()).isNull();
    }

    @Test
    void retryResetsAFailedUploadAndAllowsProcessingToFinish() throws Exception {
        User user = createUser("async-retry@example.com");
        Document document = new Document();
        document.setName("retry-case.txt");
        document.setOwner(user);
        document.updateProcessingState(ProcessingStatus.FAILED, "Initial processing failed.");
        document = documentRepository.save(document);

        Path stagedFile = Files.createTempFile("filemind-retry-stage-", ".txt");
        Files.writeString(stagedFile, "Retry processing should finish successfully.");
        String contentHash = com.filemind.chunk.util.ChunkHashUtils.sha256(stagedFile);

        DocumentVersion failedVersion = versionService.queueUpload(
            document,
            user,
            stagedFile,
            Files.size(stagedFile),
            contentHash,
            "retry-case.txt",
            "text/plain",
            "Retry simulation",
            VersionOrigin.UPLOAD
        );
        failedVersion.setProcessingStatus(ProcessingStatus.FAILED);
        failedVersion.setProcessingError("Simulated failure");
        failedVersion.setCurrentVersion(false);
        documentVersionRepository.save(failedVersion);

        UploadDocumentResponse retryResponse = documentService.retryFailedProcessing(user, document.getId());

        assertThat(retryResponse.status()).isEqualTo(ProcessingStatus.UPLOADED.name());
        assertThat(retryResponse.versionId()).isEqualTo(failedVersion.getId());

        DocumentProcessingStatusResponse finalStatus =
            DocumentProcessingTestSupport.waitForTerminalStatus(documentService, user, document.getId());

        assertThat(finalStatus.status()).isEqualTo(ProcessingStatus.COMPLETED.name());
        assertThat(finalStatus.readyForViewing()).isTrue();

        DocumentVersion refreshedVersion = documentVersionRepository.findById(failedVersion.getId()).orElseThrow();
        assertThat(refreshedVersion.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(refreshedVersion.getManifestPath()).isNotBlank();
        assertThat(refreshedVersion.getProcessingError()).isNull();
    }

    private User createUser(String email) {
        User user = new User();
        user.setFullName("Async Upload User");
        user.setEmail(email);
        user.setPasswordHash("encoded");
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }
}
