package com.filemind.chunk.format;

import com.filemind.chunk.model.ManifestChunk;
import com.filemind.chunk.model.ChunkPiece;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface FormatChunkStore {

    ManifestChunk storeChunkPiece(ChunkPiece chunkPiece) throws IOException;

    List<ManifestChunk> storeChunked(Path filePath) throws IOException;

    ManifestChunk storeWhole(Path filePath) throws IOException;
}
