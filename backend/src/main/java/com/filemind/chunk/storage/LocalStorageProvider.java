package com.filemind.chunk.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.chunk.model.ChunkStorageResult;
import com.filemind.chunk.model.StoredManifest;
import com.filemind.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
@RequiredArgsConstructor
public class LocalStorageProvider implements StorageProvider {

    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean chunkExists(String hash) {
        return Files.exists(chunkPath(hash, false)) || Files.exists(chunkPath(hash, true));
    }

    @Override
    public ChunkStorageResult storeChunk(String hash, byte[] bytes) {
        Path rawPath = chunkPath(hash, false);
        Path gzipPath = chunkPath(hash, true);
        try {
            if (Files.exists(gzipPath)) {
                return new ChunkStorageResult(gzipPath.toString(), Files.size(gzipPath));
            }
            if (Files.exists(rawPath)) {
                return new ChunkStorageResult(rawPath.toString(), Files.size(rawPath));
            }

            byte[] compressed = gzip(bytes);
            boolean useCompression = compressed.length < bytes.length;
            Path path = useCompression ? gzipPath : rawPath;
            Files.write(path, useCompression ? compressed : bytes);
            return new ChunkStorageResult(path.toString(), Files.size(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist chunk " + hash, exception);
        }
    }

    @Override
    public ChunkStorageResult storeChunk(String hash, Path filePath) {
        Path rawPath = chunkPath(hash, false);
        Path gzipPath = chunkPath(hash, true);
        Path rawTempPath = null;
        Path gzipTempPath = null;
        try {
            if (Files.exists(gzipPath)) {
                return new ChunkStorageResult(gzipPath.toString(), Files.size(gzipPath));
            }
            if (Files.exists(rawPath)) {
                return new ChunkStorageResult(rawPath.toString(), Files.size(rawPath));
            }

            rawTempPath = Files.createTempFile(Path.of(storageProperties.getChunks()), hash + "-", ".rawtmp");
            gzipTempPath = Files.createTempFile(Path.of(storageProperties.getChunks()), hash + "-", ".gztmp");

            try (InputStream inputStream = Files.newInputStream(filePath);
                 OutputStream rawOutputStream = Files.newOutputStream(rawTempPath, StandardOpenOption.TRUNCATE_EXISTING);
                 GZIPOutputStream gzipOutputStream = new GZIPOutputStream(Files.newOutputStream(gzipTempPath, StandardOpenOption.TRUNCATE_EXISTING))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    rawOutputStream.write(buffer, 0, read);
                    gzipOutputStream.write(buffer, 0, read);
                }
            }

            long rawSize = Files.size(rawTempPath);
            long gzipSize = Files.size(gzipTempPath);
            boolean useCompression = gzipSize < rawSize;
            Path selectedTempPath = useCompression ? gzipTempPath : rawTempPath;
            Path rejectedTempPath = useCompression ? rawTempPath : gzipTempPath;
            Path finalPath = useCompression ? gzipPath : rawPath;
            Files.move(selectedTempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(rejectedTempPath);
            return new ChunkStorageResult(finalPath.toString(), Files.size(finalPath));
        } catch (IOException exception) {
            try {
                if (rawTempPath != null) {
                    Files.deleteIfExists(rawTempPath);
                }
                if (gzipTempPath != null) {
                    Files.deleteIfExists(gzipTempPath);
                }
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("Unable to persist chunk " + hash, exception);
        }
    }

    @Override
    public byte[] readChunk(String storagePath) {
        try {
            byte[] storedBytes = Files.readAllBytes(Path.of(storagePath));
            if (storagePath.endsWith(".gz")) {
                return gunzip(storedBytes);
            }
            return storedBytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read stored chunk", exception);
        }
    }

    @Override
    public void deleteChunk(String storagePath) {
        try {
            Files.deleteIfExists(Path.of(storagePath));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete stored chunk", exception);
        }
    }

    @Override
    public void deleteChunkByHash(String hash) {
        try {
            Files.deleteIfExists(chunkPath(hash, false));
            Files.deleteIfExists(chunkPath(hash, true));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete stored chunk " + hash, exception);
        }
    }

    @Override
    public String writeManifest(String versionId, StoredManifest manifest) {
        Path path = Path.of(storageProperties.getManifests(), versionId + ".json");
        try {
            objectMapper.writeValue(path.toFile(), manifest);
            return path.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write manifest", exception);
        }
    }

    @Override
    public StoredManifest readManifest(String manifestPath) {
        try {
            return objectMapper.readValue(Path.of(manifestPath).toFile(), StoredManifest.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read manifest", exception);
        }
    }

    @Override
    public void deleteManifest(String manifestPath) {
        try {
            Files.deleteIfExists(Path.of(manifestPath));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete manifest", exception);
        }
    }

    private Path chunkPath(String hash, boolean compressed) {
        return Path.of(storageProperties.getChunks(), hash + (compressed ? ".gz" : ".bin"));
    }

    private byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream)) {
            gzipStream.write(bytes);
        }
        return outputStream.toByteArray();
    }

    private byte[] gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream gzipStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            gzipStream.transferTo(outputStream);
            return outputStream.toByteArray();
        }
    }
}
