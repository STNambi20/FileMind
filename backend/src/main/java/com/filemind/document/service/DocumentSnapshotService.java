package com.filemind.document.service;

import com.filemind.chunk.model.DeduplicationMetrics;
import com.filemind.document.dto.DocumentStorageStats;
import com.filemind.document.entity.Document;
import com.filemind.document.model.ProcessingStatus;
import com.filemind.metrics.service.MetricsService;
import com.filemind.version.entity.DocumentVersion;
import com.filemind.version.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentSnapshotService {

    private final DocumentVersionRepository documentVersionRepository;
    private final MetricsService metricsService;

    @Transactional(readOnly = true)
    public DocumentSnapshot load(Document document) {
        return buildSnapshot(document, documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(document.getId()));
    }

    @Transactional(readOnly = true)
    public Map<String, DocumentSnapshot> loadAll(Collection<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Map.of();
        }

        List<Document> orderedDocuments = documents.stream().toList();
        Map<String, List<DocumentVersion>> versionsByDocumentId = loadVersionsByDocumentId(orderedDocuments);

        Map<String, DocumentSnapshot> snapshots = new LinkedHashMap<>();
        for (Document document : orderedDocuments) {
            snapshots.put(document.getId(), buildSnapshot(document, versionsByDocumentId.getOrDefault(document.getId(), List.of())));
        }
        return snapshots;
    }

    @Transactional(readOnly = true)
    public Map<String, DocumentSnapshot> loadAllAvailable(Collection<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Map.of();
        }

        List<Document> orderedDocuments = documents.stream().toList();
        Map<String, List<DocumentVersion>> versionsByDocumentId = loadVersionsByDocumentId(orderedDocuments);

        Map<String, DocumentSnapshot> snapshots = new LinkedHashMap<>();
        for (Document document : orderedDocuments) {
            buildSnapshotIfAvailable(document, versionsByDocumentId.getOrDefault(document.getId(), List.of()))
                .filter(DocumentSnapshot::readyForViewing)
                .ifPresent(snapshot -> snapshots.put(document.getId(), snapshot));
        }
        return snapshots;
    }

    private Map<String, List<DocumentVersion>> loadVersionsByDocumentId(List<Document> orderedDocuments) {
        return documentVersionRepository
            .findByDocumentIdInOrderByDocumentIdAscVersionNumberDesc(orderedDocuments.stream().map(Document::getId).toList())
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(version -> version.getDocument().getId(), LinkedHashMap::new, java.util.stream.Collectors.toList()));
    }

    private java.util.Optional<DocumentSnapshot> buildSnapshotIfAvailable(Document document, List<DocumentVersion> versions) {
        if (versions.isEmpty()) {
            return java.util.Optional.of(emptySnapshot(document));
        }

        return java.util.Optional.of(buildSnapshotInternal(document, versions));
    }

    private DocumentSnapshot buildSnapshot(Document document, List<DocumentVersion> versions) {
        if (versions.isEmpty()) {
            return emptySnapshot(document);
        }

        return buildSnapshotInternal(document, versions);
    }

    private DocumentSnapshot buildSnapshotInternal(Document document, List<DocumentVersion> versions) {
        List<DocumentVersion> completedVersions = versions.stream()
            .filter(version -> version.getProcessingStatus() == ProcessingStatus.COMPLETED)
            .toList();
        DocumentVersion currentVersion = completedVersions.stream()
            .filter(DocumentVersion::isCurrentVersion)
            .findFirst()
            .orElse(null);
        DocumentVersion latestVersion = versions.get(0);
        DeduplicationMetrics metrics = metricsService.analyzeVersions(completedVersions);
        DocumentStorageStats storageStats = metricsService.toDocumentStorageStats(metrics);
        return new DocumentSnapshot(document, versions, completedVersions, currentVersion, latestVersion, storageStats);
    }

    private DocumentSnapshot emptySnapshot(Document document) {
        return new DocumentSnapshot(
            document,
            List.of(),
            List.of(),
            null,
            null,
            new DocumentStorageStats(0L, 0L, 0L, 0.0d, 0L, 0L, 0L, 0.0d, 0.0d, 0.0d)
        );
    }

    public record DocumentSnapshot(
        Document document,
        List<DocumentVersion> versions,
        List<DocumentVersion> completedVersions,
        DocumentVersion currentVersion,
        DocumentVersion latestVersion,
        DocumentStorageStats storageStats
    ) {
        public int totalVersions() {
            return completedVersions.size();
        }

        public boolean readyForViewing() {
            return currentVersion != null;
        }

        public boolean hasVersionData() {
            return latestVersion != null;
        }
    }
}
