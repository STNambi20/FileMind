package com.filemind.dashboard.service;

import com.filemind.activity.dto.ActivityItemResponse;
import com.filemind.activity.service.ActivityService;
import com.filemind.chunk.model.DeduplicationMetrics;
import com.filemind.collaboration.service.DocumentAccessService;
import com.filemind.dashboard.dto.DashboardDocumentResponse;
import com.filemind.dashboard.dto.RecentAccessDocumentResponse;
import com.filemind.dashboard.dto.DashboardStatsResponse;
import com.filemind.document.entity.Document;
import com.filemind.document.service.DocumentSnapshotService;
import com.filemind.metrics.service.MetricsService;
import com.filemind.user.entity.User;
import com.filemind.version.entity.DocumentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DocumentAccessService documentAccessService;
    private final DocumentSnapshotService documentSnapshotService;
    private final ActivityService activityService;
    private final MetricsService metricsService;

    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboard(User user) {
        List<Document> documents = documentAccessService.listAccessibleDocuments(user);
        Map<String, DocumentSnapshotService.DocumentSnapshot> snapshots = documentSnapshotService.loadAll(documents);
        List<Document> displayableDocuments = documents.stream()
            .filter(document -> {
                DocumentSnapshotService.DocumentSnapshot snapshot = snapshots.get(document.getId());
                return snapshot != null && snapshot.hasVersionData();
            })
            .toList();
        List<DocumentVersion> versions = snapshots.values().stream()
            .filter(DocumentSnapshotService.DocumentSnapshot::hasVersionData)
            .flatMap(snapshot -> snapshot.completedVersions().stream())
            .toList();
        List<String> documentIds = displayableDocuments.stream().map(Document::getId).toList();

        DeduplicationMetrics workspaceMetrics = metricsService.analyzeVersions(versions);

        long aiProcessedCount = versions.stream()
            .filter(version -> (version.getSummary() != null && !version.getSummary().isBlank())
                || (version.getTagsJson() != null && !version.getTagsJson().isBlank()))
            .count();

        List<DashboardDocumentResponse> recentDocuments = displayableDocuments.stream()
            .sorted(Comparator.comparing(Document::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .limit(5)
            .map(document -> {
                DocumentSnapshotService.DocumentSnapshot snapshot = snapshots.get(document.getId());
                DocumentVersion displayVersion = snapshot.currentVersion() != null ? snapshot.currentVersion() : snapshot.latestVersion();
                return new DashboardDocumentResponse(
                    document.getId(),
                    document.getName(),
                    displayVersion != null ? displayVersion.getVersionNumber() : 0,
                    snapshot.totalVersions(),
                    snapshot.storageStats().logicalSize(),
                    snapshot.storageStats().actualStoredSize(),
                    snapshot.storageStats().dedupSavedSize(),
                    snapshot.storageStats().dedupRatio(),
                    snapshot.storageStats().uniqueChunkCount(),
                    snapshot.storageStats().reusedChunkReferences(),
                    document.getUpdatedAt()
                );
            })
            .toList();

        List<RecentAccessDocumentResponse> recentAccessDocuments = displayableDocuments.stream()
            .filter(document -> {
                DocumentSnapshotService.DocumentSnapshot snapshot = snapshots.get(document.getId());
                return snapshot != null && snapshot.readyForViewing();
            })
            .filter(document -> document.getLastViewedAt() != null)
            .sorted(Comparator.comparing(Document::getLastViewedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .limit(5)
            .map(document -> {
                DocumentSnapshotService.DocumentSnapshot snapshot = snapshots.get(document.getId());
                return new RecentAccessDocumentResponse(
                    document.getId(),
                    document.getName(),
                    snapshot.currentVersion().getVersionNumber(),
                    snapshot.currentVersion().getMediaType(),
                    document.getLastViewedAt(),
                    document.getLastViewedByName(),
                    document.getLastViewedByEmail()
                );
            })
            .toList();

        List<ActivityItemResponse> recentActivity = activityService.recentWorkspaceActivity(user, documentIds);

        return new DashboardStatsResponse(
            displayableDocuments.size(),
            versions.size(),
            workspaceMetrics.logicalSize(),
            workspaceMetrics.actualStoredSize(),
            workspaceMetrics.dedupSavedSize(),
            workspaceMetrics.dedupRatio(),
            workspaceMetrics.totalChunkReferences(),
            workspaceMetrics.uniqueChunkCount(),
            workspaceMetrics.reusedChunkReferences(),
            workspaceMetrics.averageChunkSizeBytes(),
            workspaceMetrics.averageStoredChunkSizeBytes(),
            workspaceMetrics.compressionRatio(),
            aiProcessedCount,
            recentDocuments,
            recentAccessDocuments,
            recentActivity
        );
    }
}
