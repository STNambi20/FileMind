package com.filemind.chunk;

import com.filemind.chunk.model.ChunkPiece;
import com.filemind.chunk.strategy.FixedSizeChunkingStrategy;
import com.filemind.chunk.strategy.ContentDefinedChunkingStrategy;
import com.filemind.config.StorageProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ContentDefinedChunkingStrategyTest {

    @Test
    void producesVariableChunksAroundConfiguredAverage() {
        StorageProperties properties = new StorageProperties();
        properties.setCdcMinChunkSize(2048);
        properties.setCdcAvgChunkSize(4096);
        properties.setCdcMaxChunkSize(8192);

        ContentDefinedChunkingStrategy strategy = new ContentDefinedChunkingStrategy(properties);
        byte[] content = buildLargeDocument("approved for policy review");
        List<ChunkPiece> chunks = strategy.chunk(content);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(8);
        assertThat(reconstruct(chunks)).isEqualTo(content);
        assertThat(chunks.stream().map(ChunkPiece::size).distinct().count()).isGreaterThan(3);
        assertThat(chunks.stream().limit(Math.max(0, chunks.size() - 1L)).allMatch(chunk -> chunk.size() >= 2048 && chunk.size() <= 8192))
            .isTrue();

        double averageChunkSize = chunks.stream().mapToInt(ChunkPiece::size).average().orElse(0D);
        assertThat(averageChunkSize).isBetween(2800D, 6200D);
    }

    @Test
    void preservesMoreReusableChunksThanFixedSizeWhenContentShifts() {
        StorageProperties cdcProperties = new StorageProperties();
        cdcProperties.setCdcMinChunkSize(2048);
        cdcProperties.setCdcAvgChunkSize(4096);
        cdcProperties.setCdcMaxChunkSize(8192);

        StorageProperties fixedProperties = new StorageProperties();
        fixedProperties.setChunkSize(4096);

        ContentDefinedChunkingStrategy cdcStrategy = new ContentDefinedChunkingStrategy(cdcProperties);
        FixedSizeChunkingStrategy fixedStrategy = new FixedSizeChunkingStrategy(fixedProperties);

        byte[] original = buildLargeDocument("");
        byte[] revised = buildLargeDocument("Inserted approval note for legal and finance review before the roadmap baseline.\n".repeat(18));

        int cdcSharedHashes = sharedHashes(cdcStrategy.chunk(original), cdcStrategy.chunk(revised));
        int fixedSharedHashes = sharedHashes(fixedStrategy.chunk(original), fixedStrategy.chunk(revised));

        assertThat(cdcSharedHashes).isGreaterThan(fixedSharedHashes);
        assertThat(cdcSharedHashes).isGreaterThan(4);
    }

    @Test
    void preservesBoundariesAcrossLocalEditsAndReusesChunks() {
        StorageProperties properties = new StorageProperties();
        properties.setCdcMinChunkSize(2048);
        properties.setCdcAvgChunkSize(4096);
        properties.setCdcMaxChunkSize(8192);

        ContentDefinedChunkingStrategy strategy = new ContentDefinedChunkingStrategy(properties);

        byte[] original = buildLargeDocument("approved for policy review");
        byte[] revised = buildLargeDocument("approved for policy review with finance sign-off");

        List<ChunkPiece> originalChunks = strategy.chunk(original);
        List<ChunkPiece> revisedChunks = strategy.chunk(revised);

        assertThat(originalChunks).hasSizeGreaterThanOrEqualTo(8);
        assertThat(revisedChunks).hasSizeGreaterThanOrEqualTo(8);
        assertThat(reconstruct(originalChunks)).isEqualTo(original);
        assertThat(reconstruct(revisedChunks)).isEqualTo(revised);
        assertThat(sharedHashes(originalChunks, revisedChunks)).isGreaterThan(2);
    }

    @Test
    void producesTheSameChunksForTheSameInputAcrossRuns() {
        StorageProperties properties = new StorageProperties();
        properties.setCdcMinChunkSize(2048);
        properties.setCdcAvgChunkSize(4096);
        properties.setCdcMaxChunkSize(8192);

        ContentDefinedChunkingStrategy strategy = new ContentDefinedChunkingStrategy(properties);
        byte[] content = buildLargeDocument("deterministic storage verification block");

        List<ChunkPiece> firstRun = strategy.chunk(content);
        List<ChunkPiece> secondRun = strategy.chunk(content);

        assertThat(firstRun).hasSameSizeAs(secondRun);
        assertThat(firstRun.stream().map(ChunkPiece::hash).toList()).containsExactlyElementsOf(secondRun.stream().map(ChunkPiece::hash).toList());
        assertThat(firstRun.stream().map(ChunkPiece::size).toList()).containsExactlyElementsOf(secondRun.stream().map(ChunkPiece::size).toList());
    }

    private int sharedHashes(List<ChunkPiece> left, List<ChunkPiece> right) {
        Set<String> leftHashes = new HashSet<>(left.stream().map(ChunkPiece::hash).toList());
        Set<String> rightHashes = new HashSet<>(right.stream().map(ChunkPiece::hash).toList());
        leftHashes.retainAll(rightHashes);
        return leftHashes.size();
    }

    private byte[] buildLargeDocument(String insertedBlock) {
        StringBuilder builder = new StringBuilder();
        IntStream.range(0, 180).forEach(index -> builder
            .append("Section ").append(index).append(": ")
            .append("FileMind uses content-defined chunking to preserve reuse after small document edits. ")
            .append("Each section contains stable wording and a unique marker ").append(index).append(". ")
            .append("Storage metrics remain consistent across uploads, versions, and restores.\n"));

        int insertionPoint = Math.min(builder.length(), 1400);
        builder.insert(insertionPoint, insertedBlock);
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] reconstruct(List<ChunkPiece> chunks) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        chunks.forEach(chunk -> outputStream.writeBytes(chunk.bytes()));
        return outputStream.toByteArray();
    }
}
