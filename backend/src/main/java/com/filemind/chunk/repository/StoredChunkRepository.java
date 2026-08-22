package com.filemind.chunk.repository;

import com.filemind.chunk.entity.StoredChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoredChunkRepository extends JpaRepository<StoredChunk, String> {

    @Query("select coalesce(sum(coalesce(c.storedSizeBytes, c.sizeBytes)), 0) from StoredChunk c")
    long totalStoredBytes();
}
