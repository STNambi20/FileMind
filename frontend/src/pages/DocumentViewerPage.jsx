import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useToast } from "../app/providers/ToastProvider";
import DocumentPreviewPanel from "../components/documents/DocumentPreviewPanel";
import ActivityTimeline from "../components/dashboard/ActivityTimeline";
import PageHeader from "../components/PageHeader";
import AppIcon from "../components/ui/AppIcon";
import {
  downloadDocument,
  downloadVersion,
  getDocumentViewer,
  listVersions,
} from "../services/documentService";
import { getApiErrorMessage } from "../utils/apiError";
import { formatBytesDetailed, formatCount, formatDateTime, formatPercent } from "../utils/formatters";
import { documentTypeLabel, formatVersionOrigin } from "../utils/productPresentation";
import { subscribeWorkspaceUpdates } from "../utils/workspaceEvents";

function DocumentViewerPage() {
  const { fileId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const { showToast } = useToast();
  const versionId = searchParams.get("versionId") || "";
  const [viewer, setViewer] = useState(null);
  const [versions, setVersions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [downloading, setDownloading] = useState(false);

  function loadViewer() {
    setLoading(true);
    setError("");
    Promise.all([getDocumentViewer(fileId, versionId), listVersions(fileId)])
      .then(([viewerResponse, versionList]) => {
        setViewer(viewerResponse);
        setVersions(versionList);
      })
      .catch((requestError) => setError(getApiErrorMessage(requestError, "Unable to open the document viewer")))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadViewer();
  }, [fileId, versionId]);

  useEffect(() => {
    const unsubscribe = subscribeWorkspaceUpdates((event) => {
      if (!event.detail?.documentId || event.detail.documentId === fileId) {
        loadViewer();
      }
    });

    return unsubscribe;
  }, [fileId, versionId]);

  const selectedVersion = viewer?.version;
  const lastViewedLabel = viewer?.lastViewedAt
    ? `${viewer.lastViewedByName || "Someone"} viewed this on ${formatDateTime(viewer.lastViewedAt)}`
    : "No view activity yet.";

  async function handleDownload() {
    if (!viewer) return;

    setDownloading(true);
    try {
      if (viewer.version.currentVersion) {
        await downloadDocument(fileId, viewer.version.fileName);
      } else {
        await downloadVersion(viewer.version.id, viewer.version.fileName);
      }
      showToast({
        title: "Download started",
        description: `Version ${viewer.version.versionNumber} is being prepared.`,
      });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to download this version");
      setError(message);
      showToast({
        title: "Download failed",
        description: message,
        tone: "error",
      });
    } finally {
      setDownloading(false);
    }
  }

  function handleSelectVersion(nextVersionId, isCurrent) {
    if (isCurrent) {
      setSearchParams({});
      return;
    }
    setSearchParams({ versionId: nextVersionId });
  }

  if (loading) {
    return (
      <div>
        <PageHeader title="Viewer" subtitle="Preparing the document preview." />
        <div className="grid gap-6 xl:grid-cols-[1.18fr_0.82fr]">
          <div className="card h-[34rem] animate-pulse bg-slate-100" />
          <div className="space-y-6">
            <div className="card h-52 animate-pulse bg-slate-100" />
            <div className="card h-72 animate-pulse bg-slate-100" />
          </div>
        </div>
      </div>
    );
  }

  if (error || !viewer || !selectedVersion) {
    return (
      <div>
        <PageHeader title="Viewer" subtitle="Document preview and version context." />
        <div className="card">
          <p className="text-sm text-rose-700">{error || "Unable to open this document."}</p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title={viewer.documentName}
        subtitle="Read the selected version and move between revisions."
        action={
          <div className="flex flex-col gap-3 sm:flex-row">
            <button className="button-secondary w-full sm:w-auto" disabled={downloading} onClick={handleDownload} type="button">
              <AppIcon className="h-4 w-4" name="download" />
              {downloading ? "Preparing..." : "Download"}
            </button>
            <Link className="button-secondary w-full sm:w-auto" to={`/files/${fileId}/versions`}>
              <AppIcon className="h-4 w-4" name="restore" />
              Version history
            </Link>
            <Link className="button-primary w-full sm:w-auto" to={`/files/${fileId}`}>
              Back to details
            </Link>
          </div>
        }
      />

      <div className="grid gap-6 xl:grid-cols-[1.18fr_0.82fr]">
        <div className="space-y-6">
          <section className="card overflow-hidden bg-[linear-gradient(145deg,#06111f_0%,#0f172a_68%,#172554_100%)] text-white shadow-panel">
            <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
              <div className="min-w-0">
                <div className="flex flex-wrap gap-2">
                  <span className="label-chip border-white/10 bg-white/10 text-white">{documentTypeLabel(selectedVersion.mediaType)}</span>
                  <span className="label-chip border-white/10 bg-white/10 text-white">Version {selectedVersion.versionNumber}</span>
                  <span className="label-chip border-white/10 bg-white/10 text-white">{formatVersionOrigin(selectedVersion.origin)}</span>
                  {selectedVersion.ocrApplied ? <span className="label-chip border-white/10 bg-white/10 text-white">OCR assisted</span> : null}
                </div>
                <p className="mt-4 break-words text-3xl font-semibold tracking-[-0.05em] text-white">{selectedVersion.fileName}</p>
                <p className="mt-3 text-sm text-slate-300">{lastViewedLabel}</p>
              </div>

              <div className="grid gap-3 sm:grid-cols-3">
                <div className="rounded-3xl border border-white/10 bg-white/8 px-4 py-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-slate-300">Logical</p>
                  <p className="mt-2 text-lg font-semibold text-white">{formatBytesDetailed(selectedVersion.logicalSize)}</p>
                </div>
                <div className="rounded-3xl border border-white/10 bg-white/8 px-4 py-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-slate-300">Actual</p>
                  <p className="mt-2 text-lg font-semibold text-white">{formatBytesDetailed(selectedVersion.actualStoredSize)}</p>
                </div>
                <div className="rounded-3xl border border-white/10 bg-white/8 px-4 py-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-slate-300">Saved</p>
                  <p className="mt-2 text-lg font-semibold text-white">{formatBytesDetailed(selectedVersion.dedupSavedSize)}</p>
                </div>
              </div>
            </div>
          </section>

          <section className="card">
            <div className="flex items-center gap-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                <AppIcon className="h-4.5 w-4.5" name="eye" />
              </span>
              <div>
                <p className="section-label">Document Viewer</p>
                <p className="mt-2 text-sm text-slate-500">In-app preview for the selected version.</p>
              </div>
            </div>

            <div className="mt-6">
              <DocumentPreviewPanel fileId={fileId} viewer={viewer} />
            </div>
          </section>
        </div>

        <div className="space-y-6">
          <section className="card">
            <div className="flex items-center gap-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                <AppIcon className="h-4.5 w-4.5" name="restore" />
              </span>
              <div>
                <p className="section-label">Version Trail</p>
                <p className="mt-2 text-sm text-slate-500">Open any version directly in the viewer.</p>
              </div>
            </div>

            <div className="mt-5 space-y-3">
              {versions.map((version) => (
                <button
                  key={version.id}
                  className={`w-full rounded-[1.5rem] border px-4 py-4 text-left transition ${
                    version.id === selectedVersion.id
                      ? "border-brand-300 bg-brand-50 text-brand-700"
                      : "border-slate-200 bg-white text-slate-700 hover:border-brand-200 hover:bg-slate-50"
                  }`}
                  onClick={() => handleSelectVersion(version.id, version.currentVersion)}
                  type="button"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-medium">Version {version.versionNumber}</p>
                      <p className="mt-1 truncate text-sm opacity-80">{formatVersionOrigin(version.origin)}</p>
                    </div>
                    <div className="flex flex-wrap justify-end gap-2">
                      {version.ocrApplied ? <span className="label-chip">OCR</span> : null}
                      <span className="label-chip">{version.currentVersion ? "Current" : "Open"}</span>
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </section>

          <section className="card">
            <p className="section-label">Version Context</p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <div className="rounded-3xl bg-slate-50 px-4 py-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Created By</p>
                <p className="mt-2 text-sm font-medium text-slate-900">{selectedVersion.createdBy}</p>
              </div>
              <div className="rounded-3xl bg-slate-50 px-4 py-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Created At</p>
                <p className="mt-2 text-sm font-medium text-slate-900">{formatDateTime(selectedVersion.createdAt)}</p>
              </div>
              <div className="rounded-3xl bg-slate-50 px-4 py-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Dedup Ratio</p>
                <p className="mt-2 text-sm font-medium text-slate-900">{formatPercent(selectedVersion.dedupRatio)}</p>
              </div>
              <div className="rounded-3xl bg-slate-50 px-4 py-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Chunks</p>
                <p className="mt-2 text-sm font-medium text-slate-900">
                  {formatCount(selectedVersion.totalChunkCount)} total, {formatCount(selectedVersion.reusedChunkCount)} reused
                </p>
              </div>
            </div>

            {selectedVersion.ocrApplied ? (
              <div className="mt-4 rounded-[1.7rem] bg-amber-50 px-4 py-4">
                <p className="text-xs uppercase tracking-[0.15em] text-amber-600">OCR Assistance</p>
                <p className="mt-3 text-sm text-amber-700">This version includes OCR-derived text from image-based content.</p>
              </div>
            ) : null}

            {selectedVersion.summary ? (
              <div className="mt-4 rounded-[1.7rem] bg-slate-50 px-4 py-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Summary</p>
                <p className="mt-3 text-sm leading-7 text-slate-700">{selectedVersion.summary}</p>
              </div>
            ) : null}
          </section>

          <section className="card">
            <div className="flex items-center gap-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                <AppIcon className="h-4.5 w-4.5" name="share" />
              </span>
              <div>
                <p className="section-label">Collaboration</p>
                <p className="mt-2 text-sm text-slate-500">Owners, members, and current access for this document.</p>
              </div>
            </div>

            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              <div className="rounded-3xl bg-slate-50 px-4 py-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Owner</p>
                <p className="mt-2 text-sm font-medium text-slate-900">{viewer.ownerName}</p>
                <p className="mt-1 text-xs text-slate-500">{viewer.ownerEmail}</p>
              </div>
              <div className="rounded-3xl bg-slate-50 px-4 py-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Your Access</p>
                <p className="mt-2 text-sm font-medium text-slate-900">{viewer.permission}</p>
                <p className="mt-1 text-xs text-slate-500">
                  {viewer.canManageAccess ? "You can manage collaborators." : viewer.canEdit ? "You can edit and review versions." : "You can review and download versions."}
                </p>
              </div>
            </div>

            <div className="mt-5 space-y-3">
              {viewer.collaborators?.length ? (
                viewer.collaborators.map((collaborator) => (
                  <div key={collaborator.id} className="flex flex-col gap-3 rounded-[1.5rem] border border-slate-200 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-slate-900">{collaborator.fullName}</p>
                      <p className="mt-1 truncate text-xs text-slate-500">{collaborator.email}</p>
                    </div>
                    <span className="label-chip">{collaborator.permission}</span>
                  </div>
                ))
              ) : (
                <p className="rounded-[1.5rem] bg-slate-50 px-4 py-4 text-sm text-slate-500">No collaborators are assigned to this document yet.</p>
              )}
            </div>

            <div className="mt-5">
              <Link className="button-secondary w-full sm:w-auto" to={`/files/${fileId}`}>
                <AppIcon className="h-4 w-4" name="share" />
                Open access controls
              </Link>
            </div>
          </section>

          <section className="card">
            <div className="flex items-center gap-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                <AppIcon className="h-4.5 w-4.5" name="activity" />
              </span>
              <div>
                <p className="section-label">Recent Access</p>
                <p className="mt-2 text-sm text-slate-500">Recent views, version actions, and access updates.</p>
              </div>
            </div>

            <div className="mt-6">
              <ActivityTimeline items={viewer.recentActivity} />
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

export default DocumentViewerPage;
