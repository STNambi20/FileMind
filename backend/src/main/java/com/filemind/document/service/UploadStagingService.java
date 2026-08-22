package com.filemind.document.service;

import com.filemind.chunk.util.ChunkHashUtils;
import com.filemind.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadStagingService {

    private final StorageProperties storageProperties;

    public StagedUpload stage(MultipartFile file) throws IOException {
        return stageForProcessing(file);
    }

    public StagedUpload stageForProcessing(MultipartFile file) throws IOException {
        Path stagingDirectory = Path.of(storageProperties.getUploads()).resolve("staging");
        Files.createDirectories(stagingDirectory);

        String suffix = resolveSuffix(file.getOriginalFilename());
        Path stagedPath = stagingDirectory.resolve("upload-" + UUID.randomUUID() + suffix);
        MessageDigest digest = ChunkHashUtils.newSha256Digest();
        long sizeBytes = 0L;

        try (InputStream inputStream = new BufferedInputStream(file.getInputStream());
             OutputStream outputStream = new BufferedOutputStream(
                 Files.newOutputStream(stagedPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                sizeBytes += read;
            }
        } catch (IOException exception) {
            Files.deleteIfExists(stagedPath);
            throw exception;
        }

        return new StagedUpload(stagedPath, sizeBytes, ChunkHashUtils.toHex(digest.digest()));
    }

    private String resolveSuffix(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return ".bin";
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return ".bin";
        }

        return originalFilename.substring(dotIndex);
    }

    public void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public record StagedUpload(Path path, long sizeBytes, String contentHash) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
