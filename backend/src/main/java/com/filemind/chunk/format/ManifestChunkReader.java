package com.filemind.chunk.format;

import com.filemind.chunk.model.ManifestChunk;

public interface ManifestChunkReader {

    byte[] readChunk(ManifestChunk chunk);
}
