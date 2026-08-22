package com.filemind.chunk.format;

import com.filemind.chunk.model.ManifestStorageMode;
import com.filemind.chunk.model.StoredManifest;
import com.filemind.chunk.util.ChunkHashUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GenericBinaryStorageHandler implements FileFormatHandler {

    @Override
    public boolean supportsUpload(String fileName, String mediaType) {
        return true;
    }

    @Override
    public boolean supportsManifest(StoredManifest manifest) {
        return manifest.storageMode() == ManifestStorageMode.BINARY_CHUNKS;
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
            java.nio.file.Files.size(filePath),
            chunkStore.storeChunked(filePath)
        );
    }

    @Override
    public byte[] reconstruct(StoredManifest manifest, ManifestChunkReader chunkReader) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (int index = 0; index < manifest.chunks().size(); index += 1) {
            var chunk = manifest.chunks().get(index);
            if (chunk.order() != index) {
                throw new IllegalStateException("Manifest chunk order is invalid for " + manifest.fileName());
            }
            outputStream.writeBytes(chunkReader.readChunk(chunk));
        }
        return outputStream.toByteArray();
    }
}
