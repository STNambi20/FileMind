package com.filemind.chunk;

import com.filemind.chunk.entity.StoredChunk;
import com.filemind.chunk.format.GenericBinaryStorageHandler;
import com.filemind.chunk.model.ChunkPiece;
import com.filemind.chunk.model.ChunkStorageResult;
import com.filemind.chunk.repository.StoredChunkRepository;
import com.filemind.chunk.service.DeduplicationService;
import com.filemind.chunk.storage.StorageProvider;
import com.filemind.chunk.strategy.ChunkingStrategy;
import com.filemind.chunk.util.ChunkHashUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeduplicationServiceCleanupTest {

    @Test
    void removesNewChunkObjectsWhenChunkingFailsMidStream() throws Exception {
        byte[] firstChunk = "alpha content".getBytes(StandardCharsets.UTF_8);
        String firstHash = ChunkHashUtils.sha256(firstChunk);
        ChunkingStrategy chunkingStrategy = (inputStream, chunkConsumer) -> {
            chunkConsumer.accept(new ChunkPiece(0, firstChunk, firstHash, firstChunk.length));
            throw new IOException("Chunking failed");
        };

        StorageProvider storageProvider = mock(StorageProvider.class);
        when(storageProvider.storeChunk(anyString(), any(byte[].class)))
            .thenAnswer(invocation -> new ChunkStorageResult("chunks/" + invocation.getArgument(0), ((byte[]) invocation.getArgument(1)).length));

        StoredChunkRepository repository = mock(StoredChunkRepository.class);
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(StoredChunk.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeduplicationService deduplicationService = new DeduplicationService(
            chunkingStrategy,
            storageProvider,
            repository,
            java.util.List.of(new GenericBinaryStorageHandler())
        );
        Path file = Files.createTempFile("filemind-dedup-cleanup-", ".txt");
        Files.writeString(file, "content used to trigger cleanup");

        try {
            assertThatThrownBy(() -> deduplicationService.storeContent("version-a", "cleanup.txt", "text/plain", file))
                .hasMessageContaining("Unable to process uploaded content");
        } finally {
            Files.deleteIfExists(file);
        }

        verify(storageProvider).deleteChunkByHash(firstHash);
        verify(storageProvider, never()).writeManifest(anyString(), any());
    }

    @Test
    void removesNewChunkObjectsWhenManifestWriteFails() throws Exception {
        byte[] firstChunk = "alpha content".getBytes(StandardCharsets.UTF_8);
        String firstHash = ChunkHashUtils.sha256(firstChunk);
        ChunkingStrategy chunkingStrategy = (inputStream, chunkConsumer) ->
            chunkConsumer.accept(new ChunkPiece(0, firstChunk, firstHash, firstChunk.length));

        StorageProvider storageProvider = mock(StorageProvider.class);
        when(storageProvider.storeChunk(anyString(), any(byte[].class)))
            .thenAnswer(invocation -> new ChunkStorageResult("chunks/" + invocation.getArgument(0), ((byte[]) invocation.getArgument(1)).length));
        when(storageProvider.writeManifest(anyString(), any()))
            .thenThrow(new IllegalStateException("Manifest write failed"));

        StoredChunkRepository repository = mock(StoredChunkRepository.class);
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(StoredChunk.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeduplicationService deduplicationService = new DeduplicationService(
            chunkingStrategy,
            storageProvider,
            repository,
            java.util.List.of(new GenericBinaryStorageHandler())
        );
        Path file = Files.createTempFile("filemind-dedup-manifest-", ".txt");
        Files.writeString(file, "content used to trigger manifest cleanup");

        try {
            assertThatThrownBy(() -> deduplicationService.storeContent("version-b", "cleanup.txt", "text/plain", file))
                .hasMessageContaining("Manifest write failed");
        } finally {
            Files.deleteIfExists(file);
        }

        verify(storageProvider).deleteChunkByHash(firstHash);
        verify(storageProvider).writeManifest(anyString(), any());
    }
}
