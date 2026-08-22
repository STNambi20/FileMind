package com.filemind.chunk.strategy;

import com.filemind.chunk.model.ChunkPiece;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface ChunkingStrategy {

    void chunk(InputStream inputStream, ChunkConsumer chunkConsumer) throws IOException;

    default void chunk(Path filePath, ChunkConsumer chunkConsumer) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            chunk(inputStream, chunkConsumer);
        }
    }

    default List<ChunkPiece> chunk(byte[] bytes) {
        ChunkCollector collector = new ChunkCollector();
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            chunk(inputStream, collector);
            return collector.chunks();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to chunk in-memory content", exception);
        }
    }

    final class ChunkCollector implements ChunkConsumer {

        private final java.util.ArrayList<ChunkPiece> chunks = new java.util.ArrayList<>();

        @Override
        public void accept(ChunkPiece chunkPiece) {
            chunks.add(chunkPiece);
        }

        public List<ChunkPiece> chunks() {
            return List.copyOf(chunks);
        }
    }
}
