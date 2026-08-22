import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useConfirm } from "../app/providers/ConfirmProvider";
import { usePreferences } from "../app/providers/PreferencesProvider";
import { useToast } from "../app/providers/ToastProvider";
import ActivityTimeline from "../components/dashboard/ActivityTimeline";
import PageHeader from "../components/PageHeader";
import AppIcon from "../components/ui/AppIcon";
import {
  compareDocumentVersions,
  downloadVersion,
  getDocument,
  listVersions,
  restoreVersion,
} from "../services/documentService";
import { getApiErrorMessage } from "../utils/apiError";
import { formatBytesDetailed, formatCount, formatDateTime, formatPercent } from "../utils/formatters";
import { documentTypeLabel, formatVersionOrigin } from "../utils/productPresentation";
import { notifyWorkspaceUpdated, subscribeWorkspaceUpdates } from "../utils/workspaceEvents";
import { useDocumentOpenAction } from "../hooks/useDocumentOpenAction";

function versionMeta(version) {
  if (version.currentVersion) {
    return { icon: "check", tone: "bg-emerald-50 text-emerald-700", label: "Current" };
  }
  if (version.origin === "AI_REWRITE") {
    return { icon: "spark", tone: "bg-violet-50 text-violet-700", label: formatVersionOrigin(version.origin) };
  }
  if (version.origin === "RESTORE") {
    return { icon: "restore", tone: "bg-amber-50 text-amber-700", label: formatVersionOrigin(version.origin) };
  }
  return { icon: "upload", tone: "bg-brand-50 text-brand-700", label: formatVersionOrigin(version.origin) };
}

function VersionHistoryPage() {
  const { fileId } = useParams();
  const { showToast } = useToast();
  const { preferences } = usePreferences();
  const { openActionLabel, openDocument } = useDocumentOpenAction();
  const { confirm } = useConfirm();
  const [versions, setVersions] = useState([]);
  const [document, setDocument] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [restoringVersionId, setRestoringVersionId] = useState(null);
  const [downloadingVersionId, setDownloadingVersionId] = useState(null);
  const [compareBaseId, setCompareBaseId] = useState("");
  const [compareTargetId, setCompareTargetId] = useState("");
  const [comparison, setComparison] = useState(null);
  const [comparisonLoading, setComparisonLoading] = useState(false);

  function loadPage() {
    setLoading(true);
    setError("");
    Promise.all([listVersions(fileId), getDocument(fileId)])
      .then(([versionList, documentDetails]) => {
        setVersions(versionList);
        setDocument(documentDetails);
      })
      .catch((requestError) => setError(getApiErrorMessage(requestError, "Unable to load versions")))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadPage();
  }, [fileId]);

  useEffect(() => {
    const unsubscribe = subscribeWorkspaceUpdates((event) => {
      if (!event.detail?.documentId || event.detail.documentId === fileId) {
        loadPage();
      }
    });

    return unsubscribe;
  }, [fileId]);

  useEffect(() => {
    if (versions.length < 2) {
      setCompareBaseId("");
      setCompareTargetId("");
      setComparison(null);
      return;
    }

    if (!compareTargetId || !versions.some((version) => version.id === compareTargetId)) {
      setCompareTargetId(versions[0].id);
    }
    if (!compareBaseId || !versions.some((version) => version.id === compareBaseId) || compareBaseId === versions[0].id) {
      setCompareBaseId(versions[1].id);
    }
  }, [versions, compareBaseId, compareTargetId]);

  useEffect(() => {
    if (!compareBaseId || !compareTargetId || compareBaseId === compareTargetId) {
      setComparison(null);
      return;
    }

    setComparisonLoading(true);
    compareDocumentVersions(fileId, compareBaseId, compareTargetId)
      .then(setComparison)
      .catch((requestError) => setError(getApiErrorMessage(requestError, "Unable to compare versions")))
      .finally(() => setComparisonLoading(false));
  }, [fileId, compareBaseId, compareTargetId]);

  async function handleRestore(versionId) {
    const confirmed = await confirm({
      eyebrow: "Restore version",
      title: "Restore this version?",
      description: "The selected version will be restored as the newest version.",
      confirmLabel: "Restore version",
    });

    if (!confirmed) return;

    setRestoringVersionId(versionId);
    try {
      const response = await restoreVersion(versionId);
      notifyWorkspaceUpdated({ type: "version-restored", documentId: fileId });
      if (preferences.notifyVersionCreated) {
        showToast({
          title: "Version restored",
          description: `Version ${response.versionNumber} is now current.`,
        });
      }
      loadPage();
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to restore version");
      setError(message);
      showToast({ title: "Restore failed", description: message, tone: "error" });
    } finally {
      setRestoringVersionId(null);
    }
  }

  async function handleDownload(version) {
    setDownloadingVersionId(version.id);
    try {
      await downloadVersion(version.id, version.fileName);
      showToast({
        title: "Download started",
        description: `Version ${version.versionNumber} is being prepared.`,
      });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to download version");
      setError(message);
      showToast({ title: "Download failed", description: message, tone: "error" });
    } finally {
      setDownloadingVersionId(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Version History"
        subtitle="Review the timeline, compare versions, and restore when needed."
        action={
          <div className="flex flex-col gap-3 sm:flex-row">
            <button className="button-secondary w-full sm:w-auto" onClick={() => openDocument({ documentId: fileId, fileName: document?.name, currentVersion: true })} type="button">
              <AppIcon className="h-4 w-4" name="eye" />
              {openActionLabel} current version
            </button>
            <Link className="button-secondary w-full sm:w-auto" to={`/files/${fileId}`}>
              Back to file
            </Link>
          </div>
        }
      />

      {error ? <p className="mb-4 rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p> : null}

      {document ? (
        <div className="mb-6 grid gap-4 xl:grid-cols-[1.08fr_0.92fr]">
          <div className="card overflow-hidden bg-[linear-gradient(145deg,#06111f_0%,#0f172a_68%,#172554_100%)] text-white">
            <p className="section-label text-brand-200/80">Version Overview</p>
            <div className="mt-4 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
              <div className="min-w-0">
                <p className="truncate text-2xl font-semibold tracking-[-0.04em] text-white">{document.name}</p>
                <div className="mt-3 flex flex-wrap gap-2 text-xs">
                  <span className="label-chip border-white/10 bg-white/10 text-white">{formatCount(document.totalVersions)} versions</span>
                  <span className="label-chip border-white/10 bg-white/10 text-white">{documentTypeLabel(document.currentVersion?.mediaType)}</span>
                  {document.currentVersion?.ocrApplied ? <span className="label-chip border-white/10 bg-white/10 text-white">OCR assisted</span> : null}
                </div>
              </div>
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="rounded-3xl border border-white/10 bg-white/8 px-4 py-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-slate-300">Logical</p>
                  <p className="mt-2 text-lg font-semibold text-white">{formatBytesDetailed(document.storage.logicalSize)}</p>
                </div>
                <div className="rounded-3xl border border-white/10 bg-white/8 px-4 py-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-slate-300">Actual</p>
                  <p className="mt-2 text-lg font-semibold text-white">{formatBytesDetailed(document.storage.actualStoredSize)}</p>
                </div>
                <div className="rounded-3xl border border-white/10 bg-white/8 px-4 py-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-slate-300">Saved</p>
                  <p className="mt-2 text-lg font-semibold text-white">{formatBytesDetailed(document.storage.dedupSavedSize)}</p>
                </div>
              </div>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-2">
            <div className="card">
              <p className="section-label">Dedup Ratio</p>
              <p className="mt-3 text-3xl font-semibold tracking-[-0.04em] text-slate-950">{formatPercent(document.storage.dedupRatio)}</p>
            </div>
            <div className="card">
              <p className="section-label">Unique Chunks</p>
              <p className="mt-3 text-3xl font-semibold tracking-[-0.04em] text-slate-950">{formatCount(document.storage.uniqueChunkCount)}</p>
            </div>
            <div className="card">
              <p className="section-label">Reused Chunks</p>
              <p className="mt-3 text-3xl font-semibold tracking-[-0.04em] text-slate-950">{formatCount(document.storage.reusedChunkReferences)}</p>
            </div>
            <div className="card">
              <p className="section-label">Chunk References</p>
              <p className="mt-3 text-3xl font-semibold tracking-[-0.04em] text-slate-950">{formatCount(document.storage.totalChunkReferences)}</p>
            </div>
          </div>
        </div>
      ) : null}

      {versions.length >= 2 ? (
        <section className="card mb-6">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <p className="section-label">Version Compare</p>
              <p className="mt-2 text-sm text-slate-500">Compare extracted text and metadata between two versions.</p>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <select className="input min-w-0" value={compareBaseId} onChange={(event) => setCompareBaseId(event.target.value)}>
                {versions.map((version) => (
                  <option key={version.id} value={version.id}>
                    Base: Version {version.versionNumber}
                  </option>
                ))}
              </select>
              <select className="input min-w-0" value={compareTargetId} onChange={(event) => setCompareTargetId(event.target.value)}>
                {versions.map((version) => (
                  <option key={version.id} value={version.id}>
                    Target: Version {version.versionNumber}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="mt-6">
            {comparisonLoading ? (
              <p className="text-sm text-slate-500">Comparing versions...</p>
            ) : comparison ? (
              <div className="space-y-4">
                <p className="text-sm text-slate-600">{comparison.summary}</p>

                <div className="grid gap-3 md:grid-cols-3">
                  <div className="rounded-2xl bg-slate-50 px-4 py-4">
                    <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Shared Lines</p>
                    <p className="mt-2 text-xl font-semibold text-slate-950">{formatCount(comparison.sharedLineCount)}</p>
                  </div>
                  <div className="rounded-2xl bg-emerald-50 px-4 py-4">
                    <p className="text-xs uppercase tracking-[0.15em] text-emerald-600">Added</p>
                    <p className="mt-2 text-xl font-semibold text-emerald-700">{formatCount(comparison.addedLineCount)}</p>
                  </div>
                  <div className="rounded-2xl bg-amber-50 px-4 py-4">
                    <p className="text-xs uppercase tracking-[0.15em] text-amber-600">Removed</p>
                    <p className="mt-2 text-xl font-semibold text-amber-700">{formatCount(comparison.removedLineCount)}</p>
                  </div>
                </div>

                <div className="grid gap-4 lg:grid-cols-3">
                  <div className="rounded-[1.7rem] bg-slate-50 px-4 py-4">
                    <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Added Preview</p>
                    {comparison.addedPreview.length ? (
                      <ul className="mt-3 space-y-2 text-sm text-slate-700">
                        {comparison.addedPreview.map((line) => (
                          <li key={line}>{line}</li>
                        ))}
                      </ul>
                    ) : (
                      <p className="mt-3 text-sm text-slate-500">No added lines in the extracted preview.</p>
                    )}
                  </div>
                  <div className="rounded-[1.7rem] bg-slate-50 px-4 py-4">
                    <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Removed Preview</p>
                    {comparison.removedPreview.length ? (
                      <ul className="mt-3 space-y-2 text-sm text-slate-700">
                        {comparison.removedPreview.map((line) => (
                          <li key={line}>{line}</li>
                        ))}
                      </ul>
                    ) : (
                      <p className="mt-3 text-sm text-slate-500">No removed lines in the extracted preview.</p>
                    )}
                  </div>
                  <div className="rounded-[1.7rem] bg-slate-50 px-4 py-4">
                    <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Metadata Changes</p>
                    {comparison.metadataChanges.length ? (
                      <ul className="mt-3 space-y-2 text-sm text-slate-700">
                        {comparison.metadataChanges.map((item) => (
                          <li key={item}>{item}</li>
                        ))}
                      </ul>
                    ) : (
                      <p className="mt-3 text-sm text-slate-500">No metadata changes across these versions.</p>
                    )}
                  </div>
                </div>
              </div>
            ) : (
              <p className="text-sm text-slate-500">Choose two different versions to compare.</p>
            )}
          </div>
        </section>
      ) : null}

      {document?.recentActivity?.length ? (
        <section className="card mb-6">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
              <AppIcon className="h-4.5 w-4.5" name="activity" />
            </span>
            <div>
              <p className="section-label">Version Audit Trail</p>
              <p className="mt-2 text-sm text-slate-500">Recent version, access, and AI actions for this document.</p>
            </div>
          </div>

          <div className="mt-6">
            <ActivityTimeline items={document.recentActivity} />
          </div>
        </section>
      ) : null}

      <div className="card">
        {loading ? (
          <p className="text-sm text-slate-500">Loading version history...</p>
        ) : versions.length === 0 ? (
          <p className="rounded-2xl bg-slate-50 px-4 py-5 text-sm text-slate-500">No versions have been created for this document yet.</p>
        ) : (
          <div className="relative space-y-5">
            <div className="absolute bottom-4 left-[1.4rem] top-4 hidden w-px bg-slate-200 sm:block" />

            {versions.map((version) => {
              const meta = versionMeta(version);
              const rowBusy = restoringVersionId === version.id;
              const downloadBusy = downloadingVersionId === version.id;

              return (
                <div key={version.id} className="relative">
                  <div className="sm:pl-16">
                    <span className={`absolute left-0 top-2 hidden h-11 w-11 items-center justify-center rounded-2xl sm:flex ${meta.tone}`}>
                      <AppIcon className="h-4.5 w-4.5" name={meta.icon} />
                    </span>

                    <div className="rounded-[1.7rem] border border-slate-200 bg-white px-4 py-4 shadow-sm">
                      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="text-lg font-semibold text-slate-950">Version {version.versionNumber}</p>
                            <span className={`rounded-full px-3 py-1 text-xs font-medium ${meta.tone}`}>{meta.label}</span>
                          </div>
                          <p className="mt-2 truncate text-sm font-medium text-slate-900">{version.fileName}</p>
                          <div className="mt-3 flex flex-wrap gap-2">
                            <span className="label-chip">{documentTypeLabel(version.mediaType)}</span>
                            <span className="label-chip">{formatDateTime(version.createdAt)}</span>
                            <span className="label-chip">{version.createdBy}</span>
                            {version.ocrApplied ? <span className="label-chip">OCR assisted</span> : null}
                          </div>
                          {version.note ? <p className="mt-3 text-sm text-slate-500">{version.note}</p> : null}
                        </div>

                        <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                          <button
                            className="button-secondary"
                            onClick={() =>
                              openDocument({
                                documentId: fileId,
                                fileName: version.fileName,
                                versionId: version.currentVersion ? "" : version.id,
                                currentVersion: version.currentVersion,
                              })
                            }
                            type="button"
                          >
                            <AppIcon className="h-4 w-4" name="eye" />
                            {openActionLabel}
                          </button>
                          <button className="button-secondary" disabled={downloadBusy} onClick={() => handleDownload(version)} type="button">
                            <AppIcon className="h-4 w-4" name="download" />
                            {downloadBusy ? "Preparing..." : "Download"}
                          </button>
                          {!version.currentVersion && document?.canEdit ? (
                            <button className="button-primary" disabled={rowBusy} onClick={() => handleRestore(version.id)} type="button">
                              <AppIcon className="h-4 w-4" name="restore" />
                              {rowBusy ? "Restoring..." : "Restore"}
                            </button>
                          ) : null}
                        </div>
                      </div>

                      <div className="mt-5 grid gap-3 md:grid-cols-4">
                        <div className="rounded-2xl bg-slate-50 px-4 py-4">
                          <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Logical</p>
                          <p className="mt-1 font-medium text-slate-900">{formatBytesDetailed(version.logicalSize)}</p>
                        </div>
                        <div className="rounded-2xl bg-slate-50 px-4 py-4">
                          <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Actual</p>
                          <p className="mt-1 font-medium text-slate-900">{formatBytesDetailed(version.actualStoredSize)}</p>
                        </div>
                        <div className="rounded-2xl bg-emerald-50 px-4 py-4">
                          <p className="text-xs uppercase tracking-[0.15em] text-emerald-600">Saved</p>
                          <p className="mt-1 font-medium text-emerald-700">{formatBytesDetailed(version.dedupSavedSize)}</p>
                        </div>
                        <div className="rounded-2xl bg-slate-50 px-4 py-4">
                          <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Dedup Ratio</p>
                          <p className="mt-1 font-medium text-slate-900">{formatPercent(version.dedupRatio)}</p>
                        </div>
                      </div>

                      <div className="mt-4 flex flex-wrap gap-2">
                        <span className="label-chip">{formatCount(version.totalChunkCount)} chunks</span>
                        <span className="label-chip">{formatCount(version.newChunkCount)} new</span>
                        <span className="label-chip">{formatCount(version.reusedChunkCount)} reused</span>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

export default VersionHistoryPage;
