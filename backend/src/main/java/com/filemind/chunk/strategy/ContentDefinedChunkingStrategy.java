package com.filemind.chunk.strategy;

import com.filemind.chunk.model.ChunkPiece;
import com.filemind.chunk.util.ChunkHashUtils;
import com.filemind.config.StorageProperties;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

@RequiredArgsConstructor
public class ContentDefinedChunkingStrategy implements ChunkingStrategy {

    private static final int ROLLING_WINDOW_SIZE = 64;
    private static final long[] GEAR_TABLE = createGearTable();

    private final StorageProperties storageProperties;

    @Override
    public void chunk(InputStream inputStream, ChunkConsumer chunkConsumer) throws IOException {
        int minChunkSize = Math.max(256, storageProperties.getCdcMinChunkSize());
        int avgChunkSize = Math.max(minChunkSize + 1, storageProperties.getCdcAvgChunkSize());
        int maxChunkSize = Math.max(avgChunkSize + 1, storageProperties.getCdcMaxChunkSize());
        long earlyBoundaryMask = boundaryMask(avgChunkSize * 2);
        long lateBoundaryMask = boundaryMask(Math.max(2, avgChunkSize / 2));
        byte[] readBuffer = new byte[Math.max(8192, maxChunkSize)];
        ByteArrayOutputStream chunkBuffer = new ByteArrayOutputStream(avgChunkSize);
        RollingBuzhash rollingBuzhash = new RollingBuzhash(ROLLING_WINDOW_SIZE);
        int order = 0;
        boolean hasData = false;
        int read;

        while ((read = inputStream.read(readBuffer)) != -1) {
            hasData = true;
            for (int index = 0; index < read; index += 1) {
                byte value = readBuffer[index];
                chunkBuffer.write(value);
                rollingBuzhash.push(value);

                int chunkLength = chunkBuffer.size();
                if (chunkLength < minChunkSize || !rollingBuzhash.isReady()) {
                    if (chunkLength >= maxChunkSize) {
                        chunkConsumer.accept(createChunk(order++, chunkBuffer));
                        rollingBuzhash = new RollingBuzhash(ROLLING_WINDOW_SIZE);
                    }
                    continue;
                }

                // Content-defined boundaries re-align after local edits because the rolling window follows the byte stream.
                long activeMask = chunkLength < avgChunkSize ? earlyBoundaryMask : lateBoundaryMask;
                if ((rollingBuzhash.fingerprint() & activeMask) == 0L || chunkLength >= maxChunkSize) {
                    chunkConsumer.accept(createChunk(order++, chunkBuffer));
                    rollingBuzhash = new RollingBuzhash(ROLLING_WINDOW_SIZE);
                }
            }
        }

        if (!hasData) {
            chunkConsumer.accept(new ChunkPiece(0, new byte[0], ChunkHashUtils.sha256(new byte[0]), 0));
            return;
        }

        if (chunkBuffer.size() > 0) {
            chunkConsumer.accept(createChunk(order, chunkBuffer));
        }
    }

    private ChunkPiece createChunk(int order, ByteArrayOutputStream chunkBuffer) {
        byte[] slice = chunkBuffer.toByteArray();
        chunkBuffer.reset();
        return new ChunkPiece(order, slice, ChunkHashUtils.sha256(slice), slice.length);
    }

    private static long[] createGearTable() {
        long[] table = new long[256];
        Random random = new Random(0xF17E2026L);
        for (int index = 0; index < table.length; index += 1) {
            table[index] = random.nextLong();
        }
        return table;
    }

    private static long boundaryMask(int expectedInterval) {
        return nextPowerOfTwo(Math.max(2, expectedInterval)) - 1L;
    }

    private static int nextPowerOfTwo(int value) {
        int normalized = 1;
        while (normalized < value) {
            normalized <<= 1;
        }
        return normalized;
    }

    private static final class RollingBuzhash {

        private final int windowSize;
        private final byte[] window;
        private int count = 0;
        private int cursor = 0;
        private long fingerprint = 0L;

        private RollingBuzhash(int windowSize) {
            this.windowSize = windowSize;
            this.window = new byte[windowSize];
        }

        private void push(byte value) {
            if (count < windowSize) {
                fingerprint = Long.rotateLeft(fingerprint, 1) ^ GEAR_TABLE[value & 0xFF];
                window[count] = value;
                count += 1;
                return;
            }

            byte outgoing = window[cursor];
            window[cursor] = value;
            cursor = (cursor + 1) % windowSize;
            fingerprint = Long.rotateLeft(fingerprint, 1)
                ^ Long.rotateLeft(GEAR_TABLE[outgoing & 0xFF], windowSize)
                ^ GEAR_TABLE[value & 0xFF];
        }

        private boolean isReady() {
            return count >= windowSize;
        }

        private long fingerprint() {
            return fingerprint;
        }
    }
}
