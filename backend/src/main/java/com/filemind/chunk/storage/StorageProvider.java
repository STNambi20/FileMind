package com.filemind.chunk.storage;

import com.filemind.chunk.model.ChunkStorageResult;
import com.filemind.chunk.model.StoredManifest;

import java.nio.file.Path;

public interface StorageProvider {

    boolean chunkExists(String hash);

    ChunkStorageResult storeChunk(String hash, byte[] bytes);

    ChunkStorageResult storeChunk(String hash, Path filePath);

    byte[] readChunk(String storagePath);

    void deleteChunk(String storagePath);

    void deleteChunkByHash(String hash);

    String writeManifest(String versionId, StoredManifest manifest);

    StoredManifest readManifest(String manifestPath);

    void deleteManifest(String manifestPath);
}
