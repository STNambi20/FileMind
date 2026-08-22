package com.filemind.chunk.format;

import com.filemind.chunk.model.StoredManifest;
import com.filemind.chunk.util.ChunkHashUtils;
import com.filemind.common.util.FileTypeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ImageStorageHandler implements FileFormatHandler {

    @Override
    public boolean supportsUpload(String fileName, String mediaType) {
        return FileTypeUtils.isImageMediaType(mediaType) || FileTypeUtils.isImageFile(fileName);
    }

    @Override
    public boolean supportsManifest(StoredManifest manifest) {
        return false;
    }

    @Override
    public StoredManifest createManifest(String fileName,
                                         String mediaType,
                                         Path filePath,
                                         FormatChunkStore chunkStore) throws IOException {
        return new StoredManifest(
            ChunkHashUtils.sha256(filePath),
            fileName,
            mediaType,
            Files.size(filePath),
            java.util.List.of(chunkStore.storeWhole(filePath))
        );
    }

    @Override
    public byte[] reconstruct(StoredManifest manifest, ManifestChunkReader chunkReader) {
        throw new UnsupportedOperationException("Image manifests reuse the generic binary reconstruction path");
    }
}
