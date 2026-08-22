package com.filemind.chunk.strategy;

import com.filemind.chunk.model.ChunkPiece;
import com.filemind.chunk.util.ChunkHashUtils;
import com.filemind.config.StorageProperties;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RequiredArgsConstructor
public class FixedSizeChunkingStrategy implements ChunkingStrategy {

    private final StorageProperties storageProperties;

    @Override
    public void chunk(InputStream inputStream, ChunkConsumer chunkConsumer) throws IOException {
        int chunkSize = Math.max(1, storageProperties.getChunkSize());
        int order = 0;
        boolean hasData = false;
        byte[] readBuffer = new byte[Math.max(8192, chunkSize)];
        ByteArrayOutputStream chunkBuffer = new ByteArrayOutputStream(chunkSize);
        int read;

        // Fixed-size chunking remains available as a fallback when deterministic slices are preferred.
        while ((read = inputStream.read(readBuffer)) != -1) {
            hasData = true;
            int offset = 0;
            while (offset < read) {
                int remaining = chunkSize - chunkBuffer.size();
                int consumed = Math.min(remaining, read - offset);
                chunkBuffer.write(readBuffer, offset, consumed);
                offset += consumed;

                if (chunkBuffer.size() == chunkSize) {
                    chunkConsumer.accept(toChunk(order++, chunkBuffer));
                }
            }
        }

        // Empty files still receive a deterministic manifest entry so reconstruction remains valid.
        if (!hasData) {
            chunkConsumer.accept(new ChunkPiece(0, new byte[0], ChunkHashUtils.sha256(new byte[0]), 0));
            return;
        }

        if (chunkBuffer.size() > 0) {
            chunkConsumer.accept(toChunk(order, chunkBuffer));
        }
    }

    private ChunkPiece toChunk(int order, ByteArrayOutputStream chunkBuffer) {
        byte[] slice = chunkBuffer.toByteArray();
        chunkBuffer.reset();
        return new ChunkPiece(order, slice, ChunkHashUtils.sha256(slice), slice.length);
    }
}
