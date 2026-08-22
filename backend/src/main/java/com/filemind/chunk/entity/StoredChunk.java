package com.filemind.chunk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "stored_chunks")
@Getter
@Setter
@NoArgsConstructor
public class StoredChunk {

    @Id
    @Column(length = 128)
    private String hash;

    @Column(nullable = false)
    private long sizeBytes;

    @Column
    private Long storedSizeBytes;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false)
    private long referenceCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant lastUsedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        if (lastUsedAt == null) {
            lastUsedAt = now;
        }
    }

    public long getPhysicalSizeBytes() {
        return storedSizeBytes == null || storedSizeBytes <= 0 ? sizeBytes : storedSizeBytes;
    }

    public void markUsed(Instant usedAt) {
        this.lastUsedAt = usedAt == null ? Instant.now() : usedAt;
    }
}
