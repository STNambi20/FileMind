package com.filemind.chunk.strategy;

import com.filemind.chunk.model.ChunkPiece;

import java.io.IOException;

@FunctionalInterface
public interface ChunkConsumer {

    void accept(ChunkPiece chunkPiece) throws IOException;
}
