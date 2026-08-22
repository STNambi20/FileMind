package com.filemind.chunk;

import com.filemind.chunk.model.ChunkPiece;
import com.filemind.chunk.strategy.FixedSizeChunkingStrategy;
import com.filemind.config.StorageProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixedSizeChunkingStrategyTest {

    @Test
    void splitsContentIntoStableOrderedChunks() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setChunkSize(8);
        FixedSizeChunkingStrategy strategy = new FixedSizeChunkingStrategy(properties);

        byte[] content = "ABCDEFGH12345678ZZ".getBytes(StandardCharsets.UTF_8);
        List<ChunkPiece> chunks = strategy.chunk(content);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).order()).isEqualTo(0);
        assertThat(chunks.get(1).order()).isEqualTo(1);
        assertThat(chunks.get(2).order()).isEqualTo(2);
        assertThat(chunks.get(0).size()).isEqualTo(8);
        assertThat(chunks.get(1).size()).isEqualTo(8);
        assertThat(chunks.get(2).size()).isEqualTo(2);
        assertThat(new String(chunks.get(0).bytes(), StandardCharsets.UTF_8)).isEqualTo("ABCDEFGH");
        assertThat(new String(chunks.get(1).bytes(), StandardCharsets.UTF_8)).isEqualTo("12345678");
        assertThat(new String(chunks.get(2).bytes(), StandardCharsets.UTF_8)).isEqualTo("ZZ");
        assertThat(chunks.get(0).hash()).isEqualTo(sha256("ABCDEFGH".getBytes(StandardCharsets.UTF_8)));
        assertThat(chunks.get(1).hash()).isEqualTo(sha256("12345678".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void returnsSingleDeterministicChunkForEmptyContent() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setChunkSize(8);
        FixedSizeChunkingStrategy strategy = new FixedSizeChunkingStrategy(properties);

        List<ChunkPiece> chunks = strategy.chunk(new byte[0]);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).order()).isEqualTo(0);
        assertThat(chunks.get(0).size()).isEqualTo(0);
        assertThat(chunks.get(0).hash()).isEqualTo(sha256(new byte[0]));
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
