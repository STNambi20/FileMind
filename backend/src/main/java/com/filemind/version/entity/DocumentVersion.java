package com.filemind.version.entity;

import com.filemind.document.model.ProcessingStatus;
import com.filemind.document.entity.Document;
import com.filemind.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "document_versions")
@Getter
@Setter
@NoArgsConstructor
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String mediaType;

    @Column(length = 128)
    private String contentHash;

    @Column(length = 128)
    private String sourceContentHash;

    @Column(nullable = false)
    private long logicalSize;

    @Column(nullable = false)
    private long actualStoredSize;

    @Column(nullable = false)
    private long dedupSavedSize;

    @Column
    private Integer totalChunkCount;

    @Column
    private Integer newChunkCount;

    @Column
    private Integer reusedChunkCount;

    @Column(nullable = false)
    private boolean currentVersion;

    @Column
    private String manifestPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionOrigin origin;

    @Column(length = 512)
    private String note;

    @Lob
    @Column
    private String extractedText;

    @Lob
    @Column
    private String summary;

    @Column(length = 512)
    private String titleSuggestion;

    @Lob
    @Column
    private String tagsJson;

    @Lob
    @Column
    private String insightsJson;

    @Lob
    @Column
    private String highlightsJson;

    @Column(length = 1024)
    private String readabilityNote;

    @Lob
    @Column
    private String datesJson;

    @Lob
    @Column
    private String namesJson;

    @Lob
    @Column
    private String keywordsJson;

    @Column(nullable = false)
    private boolean ocrApplied;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingStatus processingStatus = ProcessingStatus.COMPLETED;

    @Column(length = 1024)
    private String processingError;

    @Column(length = 2048)
    private String stagedContentPath;

    @Column(nullable = false)
    private int processingRetryCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
        if (processingStatus == null) {
            processingStatus = ProcessingStatus.COMPLETED;
        }
    }
}
