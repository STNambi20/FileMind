package com.filemind.chunk.format;

import com.filemind.chunk.model.StoredManifest;

import java.io.IOException;
import java.nio.file.Path;

public interface FileFormatHandler {

    boolean supportsUpload(String fileName, String mediaType);

    boolean supportsManifest(StoredManifest manifest);

    StoredManifest createManifest(String fileName,
                                  String mediaType,
                                  Path filePath,
                                  FormatChunkStore chunkStore) throws IOException;

    byte[] reconstruct(StoredManifest manifest, ManifestChunkReader chunkReader) throws IOException;
}
