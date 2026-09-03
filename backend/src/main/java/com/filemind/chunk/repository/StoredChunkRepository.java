package com.filemind.chunk.repository;

import com.filemind.chunk.entity.StoredChunk;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import org.springframework.stereotype.Repository;

@Repository
public class StoredChunkRepository extends DynamoRepository<StoredChunk> {
    public StoredChunkRepository(DynamoMetadataStore store) { super(store, StoredChunk.class); }
    @Override protected String id(StoredChunk chunk) { return chunk.getHash(); }

    public long totalStoredBytes() { return findAll().stream().mapToLong(StoredChunk::getPhysicalSizeBytes).sum(); }
}
