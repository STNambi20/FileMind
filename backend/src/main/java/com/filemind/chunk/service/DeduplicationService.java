package com.filemind.chunk.service;

import com.filemind.chunk.entity.StoredChunk;
import com.filemind.chunk.format.FileFormatHandler;
import com.filemind.chunk.format.FormatChunkStore;
import com.filemind.chunk.format.ManifestChunkReader;
import com.filemind.chunk.model.ChunkPiece;
import com.filemind.chunk.model.ChunkStorageResult;
import com.filemind.chunk.model.DeduplicationMetrics;
import com.filemind.chunk.model.ManifestChunk;
import com.filemind.chunk.model.ManifestStorageMode;
import com.filemind.chunk.model.StoredContentResult;
import com.filemind.chunk.model.StoredManifest;
import com.filemind.chunk.repository.StoredChunkRepository;
import com.filemind.chunk.storage.StorageProvider;
import com.filemind.chunk.strategy.ChunkingStrategy;
import com.filemind.chunk.util.ChunkHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final ChunkingStrategy chunkingStrategy;
    private final StorageProvider storageProvider;
    private final StoredChunkRepository storedChunkRepository;
    private final List<FileFormatHandler> fileFormatHandlers;

    @Transactional
    public StoredContentResult storeContent(String versionId, String fileName, String mediaType, Path filePath) {
        StorageAccumulator accumulator = new StorageAccumulator();
        List<String> newChunkHashes = new ArrayList<>();
        Instant usedAt = Instant.now();
        FormatChunkStore chunkStore = new ChunkStoreSession(accumulator, newChunkHashes, usedAt);
        FileFormatHandler handler = resolveUploadHandler(fileName, mediaType);

        try {
            StoredManifest manifest = handler.createManifest(fileName, mediaType, filePath, chunkStore);
            String manifestPath = storageProvider.writeManifest(versionId, manifest);
            long logicalSize = manifest.logicalSize();
            return new StoredContentResult(
                manifestPath,
                manifest.contentHash(),
                logicalSize,
                accumulator.actualStoredSize,
                Math.max(0L, logicalSize - accumulator.actualStoredSize),
                accumulator.totalChunkCount,
                accumulator.newChunkCount,
                accumulator.reusedChunkCount,
                List.copyOf(newChunkHashes)
            );
        } catch (IOException exception) {
            cleanupNewChunks(newChunkHashes);
            throw new IllegalStateException("Unable to process uploaded content", exception);
        } catch (RuntimeException exception) {
            cleanupNewChunks(newChunkHashes);
            throw exception;
        }
    }

    @Transactional
    public StoredContentResult storeContent(String versionId, String fileName, String mediaType, byte[] bytes) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("filemind-dedup-", resolveSuffix(fileName));
            Files.write(temporaryFile, bytes);
            return storeContent(versionId, fileName, mediaType, temporaryFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to stage in-memory content", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Transactional
    public StoredContentResult cloneManifest(String sourceManifestPath, String newVersionId) {
        StoredManifest source = storageProvider.readManifest(sourceManifestPath);
        Instant usedAt = Instant.now();
        for (ManifestChunk chunk : source.allChunks()) {
            StoredChunk storedChunk = storedChunkRepository.findById(chunk.hash())
                .orElseThrow(() -> new IllegalStateException("Chunk " + chunk.hash() + " is missing"));
            storedChunk.setReferenceCount(storedChunk.getReferenceCount() + 1);
            storedChunk.markUsed(usedAt);
            storedChunkRepository.save(storedChunk);
        }

        String manifestPath = storageProvider.writeManifest(newVersionId, source);
        return new StoredContentResult(
            manifestPath,
            source.contentHash(),
            source.logicalSize(),
            0L,
            source.logicalSize(),
            source.allChunks().size(),
            0,
            source.allChunks().size(),
            List.of()
        );
    }

    public long computeStorageFootprint(Collection<String> manifestPaths) {
        return analyzeManifests(manifestPaths).actualStoredSize();
    }

    public DeduplicationMetrics analyzeManifests(Collection<String> manifestPaths) {
        if (manifestPaths == null || manifestPaths.isEmpty()) {
            return DeduplicationMetrics.empty();
        }

        Set<String> visitedHashes = new LinkedHashSet<>();
        long logicalSize = 0L;
        long totalChunkReferences = 0L;
        long actualStoredSize = 0L;
        long uniqueLogicalChunkBytes = 0L;

        for (String manifestPath : manifestPaths) {
            if (manifestPath == null || manifestPath.isBlank()) {
                continue;
            }

            StoredManifest manifest = storageProvider.readManifest(manifestPath);
            logicalSize += manifest.logicalSize();

            for (ManifestChunk chunk : manifest.allChunks()) {
                totalChunkReferences += 1;
                if (visitedHashes.add(chunk.hash())) {
                    StoredChunk storedChunk = storedChunkRepository.findById(chunk.hash())
                        .orElseThrow(() -> new IllegalStateException("Chunk " + chunk.hash() + " is missing"));
                    actualStoredSize += storedChunk.getPhysicalSizeBytes();
                    uniqueLogicalChunkBytes += storedChunk.getSizeBytes();
                }
            }
        }

        long dedupSavedSize = Math.max(0L, logicalSize - actualStoredSize);
        double dedupRatio = logicalSize == 0 ? 0D : (double) dedupSavedSize / logicalSize;
        long uniqueChunkCount = visitedHashes.size();
        double averageChunkSizeBytes = totalChunkReferences == 0 ? 0D : (double) logicalSize / totalChunkReferences;
        double averageStoredChunkSizeBytes = uniqueChunkCount == 0 ? 0D : (double) actualStoredSize / uniqueChunkCount;
        double compressionRatio = uniqueLogicalChunkBytes == 0 ? 0D : (double) actualStoredSize / uniqueLogicalChunkBytes;

        return new DeduplicationMetrics(
            logicalSize,
            actualStoredSize,
            dedupSavedSize,
            dedupRatio,
            totalChunkReferences,
            uniqueChunkCount,
            Math.max(0L, totalChunkReferences - uniqueChunkCount),
            averageChunkSizeBytes,
            averageStoredChunkSizeBytes,
            compressionRatio,
            uniqueLogicalChunkBytes
        );
    }

    @Transactional
    public byte[] reconstruct(String manifestPath) {
        StoredManifest manifest = storageProvider.readManifest(manifestPath);
        FileFormatHandler handler = resolveManifestHandler(manifest);
        Instant usedAt = Instant.now();
        List<StoredChunk> touchedChunks = new ArrayList<>();

        try {
            byte[] reconstructed = handler.reconstruct(manifest, chunk -> readVerifiedChunk(chunk, usedAt, touchedChunks));
            storedChunkRepository.saveAll(touchedChunks);

            if (manifest.storageMode() == ManifestStorageMode.BINARY_CHUNKS) {
                if (reconstructed.length != manifest.logicalSize()) {
                    throw new IllegalStateException("Reconstructed content size does not match manifest logical size");
                }
                if (!ChunkHashUtils.sha256(reconstructed).equals(manifest.contentHash())) {
                    throw new IllegalStateException("Reconstructed content failed manifest hash verification");
                }
            }

            return reconstructed;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to reconstruct manifest content", exception);
        }
    }

    public void discardStoredContent(StoredContentResult storedContentResult) {
        if (storedContentResult == null) {
            return;
        }

        storageProvider.deleteManifest(storedContentResult.manifestPath());
        for (String hash : storedContentResult.newChunkHashes()) {
            storageProvider.deleteChunkByHash(hash);
        }
    }

    @Transactional
    public void releaseManifest(String manifestPath) {
        StoredManifest manifest = storageProvider.readManifest(manifestPath);
        for (ManifestChunk chunk : manifest.allChunks()) {
            StoredChunk storedChunk = storedChunkRepository.findById(chunk.hash())
                .orElseThrow(() -> new IllegalStateException("Chunk " + chunk.hash() + " is missing"));
            long newReferenceCount = storedChunk.getReferenceCount() - 1;
            if (newReferenceCount <= 0) {
                storageProvider.deleteChunk(storedChunk.getStoragePath());
                storedChunkRepository.delete(storedChunk);
            } else {
                storedChunk.setReferenceCount(newReferenceCount);
                storedChunkRepository.save(storedChunk);
            }
        }
        storageProvider.deleteManifest(manifestPath);
    }

    private FileFormatHandler resolveUploadHandler(String fileName, String mediaType) {
        return fileFormatHandlers.stream()
            .filter(handler -> handler.supportsUpload(fileName, mediaType))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No storage handler is available for " + fileName));
    }

    private FileFormatHandler resolveManifestHandler(StoredManifest manifest) {
        return fileFormatHandlers.stream()
            .filter(handler -> handler.supportsManifest(manifest))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No reconstruction handler is available for manifest mode " + manifest.storageMode()));
    }

    private byte[] readVerifiedChunk(ManifestChunk chunk, Instant usedAt, List<StoredChunk> touchedChunks) {
        StoredChunk storedChunk = storedChunkRepository.findById(chunk.hash())
            .orElseThrow(() -> new IllegalStateException("Chunk " + chunk.hash() + " is missing"));
        byte[] content = storageProvider.readChunk(storedChunk.getStoragePath());

        if (content.length != chunk.size() || content.length != storedChunk.getSizeBytes()) {
            throw new IllegalStateException("Stored chunk size does not match manifest metadata for " + chunk.hash());
        }
        if (!ChunkHashUtils.sha256(content).equals(chunk.hash())) {
            throw new IllegalStateException("Stored chunk content failed integrity verification for " + chunk.hash());
        }

        storedChunk.markUsed(usedAt);
        touchedChunks.add(storedChunk);
        return content;
    }

    private String resolveSuffix(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".tmp";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }

    private void cleanupNewChunks(List<String> newChunkHashes) {
        for (String hash : newChunkHashes) {
            storageProvider.deleteChunkByHash(hash);
        }
    }

    private final class ChunkStoreSession implements FormatChunkStore {

        private final StorageAccumulator accumulator;
        private final List<String> newChunkHashes;
        private final Instant usedAt;

        private ChunkStoreSession(StorageAccumulator accumulator, List<String> newChunkHashes, Instant usedAt) {
            this.accumulator = accumulator;
            this.newChunkHashes = newChunkHashes;
            this.usedAt = usedAt;
        }

        @Override
        public ManifestChunk storeChunkPiece(ChunkPiece chunkPiece) throws IOException {
            persistChunk(chunkPiece.hash(), chunkPiece.size(), chunkPiece.order(), () -> storageProvider.storeChunk(chunkPiece.hash(), chunkPiece.bytes()));
            return new ManifestChunk(chunkPiece.hash(), chunkPiece.order(), chunkPiece.size());
        }

        @Override
        public List<ManifestChunk> storeChunked(Path filePath) throws IOException {
            List<ManifestChunk> chunks = new ArrayList<>();
            chunkingStrategy.chunk(filePath, chunkPiece -> chunks.add(storeChunkPiece(chunkPiece)));
            return List.copyOf(chunks);
        }

        @Override
        public ManifestChunk storeWhole(Path filePath) throws IOException {
            int size = Math.toIntExact(Files.size(filePath));
            String hash = ChunkHashUtils.sha256(filePath);
            persistChunk(hash, size, 0, () -> storageProvider.storeChunk(hash, filePath));
            return new ManifestChunk(hash, 0, size);
        }

        private void persistChunk(String hash, int size, int order, ChunkPersistOperation persistOperation) throws IOException {
            accumulator.totalChunkCount += 1;
            StoredChunk storedChunk = storedChunkRepository.findById(hash).orElse(null);
            if (storedChunk == null) {
                ChunkStorageResult storageResult = persistOperation.persist();
                storedChunk = new StoredChunk();
                storedChunk.setHash(hash);
                storedChunk.setSizeBytes(size);
                storedChunk.setStoredSizeBytes(storageResult.storedSizeBytes());
                storedChunk.setStoragePath(storageResult.storagePath());
                storedChunk.setReferenceCount(1L);
                storedChunk.markUsed(usedAt);
                storedChunkRepository.save(storedChunk);
                newChunkHashes.add(hash);
                accumulator.actualStoredSize += storageResult.storedSizeBytes();
                accumulator.newChunkCount += 1;
                return;
            }

            storedChunk.setReferenceCount(storedChunk.getReferenceCount() + 1);
            storedChunk.markUsed(usedAt);
            storedChunkRepository.save(storedChunk);
            accumulator.reusedChunkCount += 1;
        }
    }

    @FunctionalInterface
    private interface ChunkPersistOperation {

        ChunkStorageResult persist() throws IOException;
    }

    private static final class StorageAccumulator {

        private long actualStoredSize = 0L;
        private int totalChunkCount = 0;
        private int newChunkCount = 0;
        private int reusedChunkCount = 0;
    }
}
