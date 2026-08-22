package com.filemind.chunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.chunk.model.ChunkStorageResult;
import com.filemind.chunk.storage.LocalStorageProvider;
import com.filemind.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageProviderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void compressesNewChunksAndReconstructsOriginalBytes() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setChunks(tempDirectory.resolve("chunks").toString());
        properties.setManifests(tempDirectory.resolve("manifests").toString());

        Files.createDirectories(Path.of(properties.getChunks()));
        Files.createDirectories(Path.of(properties.getManifests()));

        LocalStorageProvider provider = new LocalStorageProvider(properties, new ObjectMapper());
        byte[] content = ("FileMind keeps document bytes exact while reusing repeated data blocks. ".repeat(120))
            .getBytes(StandardCharsets.UTF_8);

        ChunkStorageResult result = provider.storeChunk("sample-hash", content);

        assertThat(result.storagePath()).endsWith(".gz");
        assertThat(result.storedSizeBytes()).isLessThan(content.length);
        assertThat(provider.readChunk(result.storagePath())).isEqualTo(content);
    }
}
