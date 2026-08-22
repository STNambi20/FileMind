import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useDocumentOpenAction } from "../hooks/useDocumentOpenAction";
import ActivityTimeline from "../components/dashboard/ActivityTimeline";
import PageHeader from "../components/PageHeader";
import StatCard from "../components/StatCard";
import AppIcon from "../components/ui/AppIcon";
import { getDashboard } from "../services/dashboardService";
import { getApiErrorMessage } from "../utils/apiError";
import { formatBytes, formatBytesDetailed, formatCount, formatDateTime, formatPercent } from "../utils/formatters";
import { documentTypeLabel } from "../utils/productPresentation";
import { subscribeWorkspaceUpdates } from "../utils/workspaceEvents";

function DashboardPage() {
  const { openDocument } = useDocumentOpenAction();
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function loadDashboard() {
    setLoading(true);
    setError("");
    getDashboard()
      .then(setDashboard)
      .catch((requestError) => setError(getApiErrorMessage(requestError, "Unable to load dashboard")))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadDashboard();
    const unsubscribe = subscribeWorkspaceUpdates(() => loadDashboard());
    return unsubscribe;
  }, []);

  if (loading) {
    return (
      <div className="space-y-4">
        <PageHeader title="Dashboard" subtitle="Loading workspace metrics and activity." />
        <div className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
          <div className="card h-72 animate-pulse bg-slate-100" />
          <div className="grid gap-4 sm:grid-cols-2">
            {[1, 2, 3, 4].map((item) => (
              <div key={item} className="card h-28 animate-pulse bg-slate-100" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <PageHeader title="Dashboard" subtitle="Operational storage, access, and document intelligence." />
        <div className="card">
          <p className="app-error-banner">{error}</p>
          <button className="button-secondary mt-4" onClick={loadDashboard} type="button">
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <PageHeader title="Dashboard" subtitle="Operational storage, access, and document intelligence." />

      <div className="grid gap-4 xl:grid-cols-[1.18fr_0.82fr]">
        <section className="hero-panel">
          <div className="relative z-10 flex flex-col gap-6">
            <div className="flex flex-wrap gap-2">
              <span className="label-chip border-white/10 bg-white/10 text-white">{formatPercent(dashboard.dedupRatio)} dedup ratio</span>
              <span className="label-chip border-white/10 bg-white/10 text-white">{formatPercent(1 - dashboard.compressionRatio)} compression saved</span>
              <span className="label-chip border-white/10 bg-white/10 text-white">{formatCount(dashboard.uniqueChunkCount)} unique chunks</span>
            </div>

            <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
              <div>
                <p className="section-label text-brand-200/80">Workspace Storage</p>
                <p className="mt-4 text-4xl font-semibold tracking-[-0.06em] text-white sm:text-5xl">
                  {formatBytesDetailed(dashboard.savedBytes)}
                </p>
                <p className="mt-4 max-w-2xl text-sm leading-7 text-slate-300">
                  FileMind is actively reusing existing chunks and compressed content across your workspace versions and documents.
                </p>

                <div className="mt-6 flex flex-col gap-3 sm:flex-row">
                  <Link className="button-primary w-full sm:w-auto" to="/files">
                    <AppIcon className="h-4 w-4" name="files" />
                    Open documents
                  </Link>
                  <Link className="button-secondary w-full border-white/10 bg-white/10 text-white hover:bg-white/14 sm:w-auto" to="/activity">
                    <AppIcon className="h-4 w-4" name="activity" />
                    Review activity
                  </Link>
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-3 xl:grid-cols-1">
                {[
                  { label: "Logical Size", value: formatBytesDetailed(dashboard.logicalStorageBytes) },
                  { label: "Actual Storage", value: formatBytesDetailed(dashboard.actualStoredBytes) },
                  { label: "Reused Chunks", value: formatCount(dashboard.reusedChunkReferences) },
                ].map((item) => (
                  <div key={item.label} className="rounded-[24px] border border-white/10 bg-white/8 px-4 py-4">
                    <p className="text-xs uppercase tracking-[0.18em] text-slate-300">{item.label}</p>
                    <p className="mt-2 text-base font-semibold text-white sm:text-lg">{item.value}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

        <div className="grid gap-4 sm:grid-cols-2">
          <StatCard accent="bg-brand-50 text-brand-700" helper="Documents available in the workspace" icon="files" label="Documents" value={dashboard.totalFiles} />
          <StatCard accent="bg-emerald-50 text-emerald-700" helper="Version records tracked across every document" icon="restore" label="Versions" value={dashboard.totalVersions} />
          <StatCard accent="bg-sky-50 text-sky-700" helper="Distinct chunks stored after deduplication" icon="storage" label="Unique Chunks" value={formatCount(dashboard.uniqueChunkCount)} />
          <StatCard accent="bg-violet-50 text-violet-700" helper="Versions enriched by AI metadata or rewriting" icon="spark" label="AI Processed" value={dashboard.aiProcessedCount} />
        </div>
      </div>

      <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard accent="bg-slate-100 text-slate-700" helper="Total original source size" icon="storage" label="Logical Storage" value={formatBytesDetailed(dashboard.logicalStorageBytes)} />
        <StatCard accent="bg-slate-100 text-slate-700" helper="Physical unique chunk footprint" icon="storage" label="Actual Storage" value={formatBytesDetailed(dashboard.actualStoredBytes)} />
        <StatCard accent="bg-emerald-50 text-emerald-700" helper="Saved through reuse and compression" icon="check" label="Storage Saved" value={formatBytesDetailed(dashboard.savedBytes)} />
        <StatCard accent="bg-amber-50 text-amber-700" helper="Chunk references served from existing storage" icon="activity" label="Reused Chunks" value={formatCount(dashboard.reusedChunkReferences)} />
      </div>

      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <StatCard accent="bg-sky-50 text-sky-700" helper="Average logical chunk size across manifests" icon="storage" label="Avg Chunk Size" value={formatBytes(dashboard.averageChunkSizeBytes)} />
        <StatCard accent="bg-violet-50 text-violet-700" helper="Space reduction from compressing unique chunks" icon="storage" label="Compression Saved" value={formatPercent(1 - dashboard.compressionRatio)} />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[0.92fr_1.08fr]">
        <div className="card">
          <div className="flex items-center gap-3">
            <span className="app-icon-badge">
              <AppIcon className="h-4.5 w-4.5" name="eye" />
            </span>
            <div>
              <p className="section-label">Recent Access</p>
              <p className="app-text-muted mt-2 text-sm">Documents opened most recently across the workspace.</p>
            </div>
          </div>

          <div className="mt-6 space-y-3">
            {dashboard.recentAccessDocuments?.length ? (
              dashboard.recentAccessDocuments.map((item) => (
                <button
                  key={item.id}
                  className="interactive-card flex w-full flex-col gap-3 rounded-[24px] border border-slate-200 bg-white px-4 py-4 text-left sm:flex-row sm:items-center sm:justify-between"
                  onClick={() => openDocument({ documentId: item.id, fileName: item.name, currentVersion: true })}
                  type="button"
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-3">
                      <span className="app-icon-badge shrink-0">
                        <AppIcon className="h-4.5 w-4.5" name="eye" />
                      </span>
                      <div className="min-w-0">
                        <p className="app-text-primary truncate font-medium">{item.name}</p>
                        <p className="app-text-muted mt-1 text-sm">
                          {item.lastViewedByName || "Someone"} viewed this on {formatDateTime(item.lastViewedAt)}
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <span className="label-chip">{documentTypeLabel(item.mediaType)}</span>
                    <span className="label-chip">Version {item.currentVersionNumber}</span>
                  </div>
                </button>
              ))
            ) : (
              <div className="empty-state">
                <div className="empty-state-icon">
                  <AppIcon className="h-5 w-5" name="eye" />
                </div>
                <p className="app-text-primary mt-4 text-base font-medium">No recent access yet</p>
                <p className="app-text-muted mt-2 text-sm">Open any document in the viewer to build an access trail.</p>
              </div>
            )}
          </div>
        </div>

        <div className="card">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="section-label">Recent Documents</p>
              <p className="mt-2 text-sm text-slate-500">Latest document updates and current storage impact.</p>
            </div>
            <Link className="button-secondary w-full sm:w-auto" to="/files">
              <AppIcon className="h-4 w-4" name="files" />
              Open files
            </Link>
          </div>

          <div className="mt-6 space-y-4">
            {dashboard.recentDocuments.length === 0 ? (
              <div className="empty-state">
                <div className="empty-state-icon">
                  <AppIcon className="h-5 w-5" name="files" />
                </div>
                <p className="app-text-primary mt-4 text-base font-medium">No documents uploaded yet</p>
                <p className="app-text-muted mt-2 text-sm">Upload a document to start tracking versions, access, and deduplication impact.</p>
              </div>
            ) : (
              dashboard.recentDocuments.map((item) => (
                <Link
                  key={item.id}
                  className="interactive-card block rounded-[26px] border border-slate-200 bg-white px-4 py-4"
                  to={`/files/${item.id}`}
                >
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div className="min-w-0">
                      <div className="flex items-center gap-3">
                        <span className="app-icon-badge shrink-0">
                          <AppIcon className="h-4.5 w-4.5" name="files" />
                        </span>
                        <div className="min-w-0">
                          <p className="app-text-primary truncate font-medium">{item.name}</p>
                          <p className="app-text-muted mt-1 text-sm">Version {item.currentVersionNumber}</p>
                        </div>
                      </div>

                      <div className="mt-4 flex flex-wrap gap-2">
                        <span className="label-chip">{formatCount(item.totalVersions)} versions</span>
                        <span className="label-chip">{formatCount(item.uniqueChunkCount)} unique chunks</span>
                        <span className="label-chip">{formatCount(item.reusedChunkReferences)} reused chunks</span>
                      </div>
                    </div>

                      <div className="grid gap-3 text-sm sm:grid-cols-3 lg:min-w-[320px]">
                        <div className="surface-panel">
                          <p className="app-text-muted text-xs uppercase tracking-[0.15em]">Logical</p>
                          <p className="app-text-primary mt-1 font-medium">{formatBytes(item.logicalSize)}</p>
                        </div>
                        <div className="surface-panel">
                          <p className="app-text-muted text-xs uppercase tracking-[0.15em]">Actual</p>
                          <p className="app-text-primary mt-1 font-medium">{formatBytes(item.actualStoredSize)}</p>
                        </div>
                        <div className="app-success-surface">
                          <p className="text-xs uppercase tracking-[0.15em]">Saved</p>
                          <p className="mt-1 font-medium">{formatBytes(item.dedupSavedSize)}</p>
                        </div>
                      </div>
                  </div>
                </Link>
              ))
            )}
          </div>
        </div>
      </div>

      <div className="mt-6 card">
        <div className="flex items-center gap-3">
          <span className="app-icon-badge">
            <AppIcon className="h-4.5 w-4.5" name="activity" />
          </span>
          <div>
            <p className="section-label">Recent Activity</p>
            <p className="app-text-muted mt-2 text-sm">Workspace events in chronological order.</p>
          </div>
        </div>

        <div className="mt-6">
          <ActivityTimeline items={dashboard.recentActivity} />
        </div>
      </div>
    </div>
  );
}

export default DashboardPage;
