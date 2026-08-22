import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { usePreferences } from "../app/providers/PreferencesProvider";
import { useToast } from "../app/providers/ToastProvider";
import PageHeader from "../components/PageHeader";
import AppIcon from "../components/ui/AppIcon";
import { MAX_UPLOAD_BYTES, SUPPORTED_UPLOAD_ACCEPT, UPLOAD_SUPPORT_MESSAGE } from "../constants/uploads";
import { useDocumentOpenAction } from "../hooks/useDocumentOpenAction";
import { enableShareLink, listDocuments, uploadDocument, waitForDocumentProcessing } from "../services/documentService";
import { getApiErrorMessage } from "../utils/apiError";
import { formatBytes, formatCount, formatDateTime, formatPercent } from "../utils/formatters";
import { documentTypeLabel } from "../utils/productPresentation";
import { notifyWorkspaceUpdated, subscribeWorkspaceUpdates } from "../utils/workspaceEvents";

function sortDocuments(documents, sortBy) {
  const items = [...documents];
  if (sortBy === "name") {
    return items.sort((left, right) => left.name.localeCompare(right.name));
  }
  if (sortBy === "size") {
    return items.sort((left, right) => right.logicalSize - left.logicalSize);
  }
  return items.sort((left, right) => new Date(right.updatedAt) - new Date(left.updatedAt));
}

function permissionTone(permission) {
  if (permission === "EDITOR" || permission === "OWNER") {
    return "bg-emerald-50 text-emerald-700";
  }
  return "bg-slate-100 text-slate-600";
}

function uploadProgressLabel(uploadProgress) {
  if (uploadProgress >= 100) {
    return "Upload complete. Processing started...";
  }
  if (uploadProgress > 0) {
    return `Uploading ${uploadProgress}%`;
  }
  return "Preparing upload...";
}

function processingTone(status) {
  if (status === "FAILED") {
    return "bg-rose-50 text-rose-700";
  }
  if (status === "COMPLETED") {
    return "bg-emerald-50 text-emerald-700";
  }
  return "bg-amber-50 text-amber-700";
}

function processingLabel(document) {
  if (document.readyForViewing) {
    return "Ready";
  }
  if (document.processingStatus === "FAILED") {
    return "Failed";
  }
  if (document.processingStatus === "PROCESSING") {
    return "Processing";
  }
  return "Queued";
}

function renderHighlightedSnippet(snippet, query) {
  if (!snippet || !query?.trim()) {
    return snippet;
  }

  const tokens = Array.from(
    new Set(
      query
        .trim()
        .split(/\s+/)
        .map((token) => token.trim())
        .filter((token) => token.length >= 2),
    ),
  );

  if (!tokens.length) {
    return snippet;
  }

  const escapedPattern = tokens.map((token) => token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|");
  const splitPattern = new RegExp(`(${escapedPattern})`, "ig");
  const matchPattern = new RegExp(`^(${escapedPattern})$`, "i");
  return snippet.split(splitPattern).map((part, index) => (
    matchPattern.test(part)
      ? <mark key={`${part}-${index}`} className="rounded bg-brand-100 px-0.5 text-brand-800">{part}</mark>
      : <span key={`${part}-${index}`}>{part}</span>
  ));
}

function MyFilesPage() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { preferences } = usePreferences();
  const { openActionLabel, openDocument } = useDocumentOpenAction();
  const [documents, setDocuments] = useState([]);
  const [query, setQuery] = useState("");
  const [type, setType] = useState("");
  const [sortBy, setSortBy] = useState("updated");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [processingUpload, setProcessingUpload] = useState(null);

  function loadDocuments({ nextQuery = query, nextType = type } = {}) {
    setLoading(true);
    setError("");
    listDocuments({ query: nextQuery, type: nextType || undefined })
      .then(setDocuments)
      .catch((requestError) => setError(getApiErrorMessage(requestError, "Unable to load documents")))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadDocuments({ nextQuery: "", nextType: "" });
  }, []);

  useEffect(() => {
    const unsubscribe = subscribeWorkspaceUpdates(() => loadDocuments({ nextQuery: query, nextType: type }));
    return unsubscribe;
  }, [query, type]);

  const visibleDocuments = useMemo(() => sortDocuments(documents, sortBy), [documents, sortBy]);
  const totalSaved = useMemo(
    () => visibleDocuments.reduce((sum, document) => sum + (document.dedupSavedSize || 0), 0),
    [visibleDocuments],
  );
  const editableCount = useMemo(() => visibleDocuments.filter((document) => document.canEdit).length, [visibleDocuments]);

  async function applyDefaultShare(documentId) {
    if (preferences.defaultVisibility !== "SHARED") {
      return;
    }

    await enableShareLink(documentId, {
      expiresInDays: preferences.defaultShareExpiryDays,
    });
  }

  async function handleAfterUpload(documentId, fileName) {
    if (preferences.afterUploadBehavior === "GO_TO_DETAILS") {
      navigate(`/files/${documentId}`);
      return;
    }
    if (preferences.afterUploadBehavior === "OPEN_FILE") {
      await openDocument({ documentId, fileName, currentVersion: true });
    }
  }

  async function handlePrimaryDocumentAction(document) {
    await openDocument({
      documentId: document.id,
      fileName: document.name,
      currentVersion: true,
    });
  }

  async function handleUpload(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (Number.isFinite(MAX_UPLOAD_BYTES) && file.size > MAX_UPLOAD_BYTES) {
      const message = "Choose a smaller file for upload.";
      setError(message);
      showToast({
        title: "Upload blocked",
        description: message,
        tone: "error",
      });
      event.target.value = "";
      return;
    }

    setUploading(true);
    try {
      const response = await uploadDocument(file, setUploadProgress);
      setUploading(false);
      setProcessingUpload({
        documentId: response.documentId,
        fileName: file.name,
        status: response.status,
        message: response.message,
      });
      setUploadProgress(100);
      loadDocuments();
      showToast({
        title: "Upload received",
        description: `${file.name} is being processed in the background.`,
      });

      const finalStatus = await waitForDocumentProcessing(response.documentId, {
        onUpdate: (status) => {
          setProcessingUpload({
            documentId: response.documentId,
            fileName: file.name,
            status: status.status,
            message: status.message,
          });
        },
      });

      setUploadProgress(0);
      setProcessingUpload(null);
      if (finalStatus.status !== "COMPLETED") {
        const message = finalStatus.message || "The upload failed during processing.";
        setError(message);
        if (preferences.notifyProcessingFailed) {
          showToast({
            title: "Processing failed",
            description: message,
            tone: "error",
          });
        }
        loadDocuments();
        return;
      }

      try {
        await applyDefaultShare(response.documentId);
      } catch {
        showToast({
          title: "Share defaults skipped",
          description: "The document finished processing, but the default public link could not be enabled.",
          tone: "warning",
        });
      }

      notifyWorkspaceUpdated({ type: "document-uploaded" });
      loadDocuments();
      if (preferences.notifyUploadCompleted) {
        showToast({
          title: "Document ready",
          description: finalStatus.message || `${file.name} is ready to open.`,
        });
      }
      await handleAfterUpload(response.documentId, file.name);
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Upload failed");
      setError(message);
      showToast({
        title: "Upload failed",
        description: message,
        tone: "error",
      });
      setUploadProgress(0);
      setProcessingUpload(null);
      loadDocuments();
    } finally {
      setUploading(false);
      event.target.value = "";
    }
  }

  function handleSearch(event) {
    event.preventDefault();
    loadDocuments();
  }

  return (
    <div>
      <PageHeader
        title="My Files"
        subtitle="Search, open, and manage your documents."
        action={
          <label className={`button-primary w-full cursor-pointer sm:w-auto ${uploading ? "pointer-events-none opacity-70" : ""}`}>
            <AppIcon className="h-4 w-4" name="upload" />
            {uploading ? "Uploading..." : "Upload document"}
            <input aria-label="Upload document" className="sr-only" accept={SUPPORTED_UPLOAD_ACCEPT} disabled={uploading} onChange={handleUpload} type="file" />
          </label>
        }
      />

      <div className="grid gap-4 2xl:grid-cols-[1.08fr_0.92fr]">
        <div className="card">
          <form className="grid gap-3 xl:grid-cols-[minmax(0,1fr)_180px_180px_auto]" onSubmit={handleSearch}>
            <label className="relative block">
              <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400">
                <AppIcon className="h-4 w-4" name="search" />
              </span>
              <input
                className="input pl-11"
                placeholder="Search documents"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
            </label>
            <select className="input" value={type} onChange={(event) => setType(event.target.value)}>
              <option value="">All file types</option>
              <option value="text/plain">TXT</option>
              <option value="application/pdf">PDF</option>
              <option value="application/vnd.openxmlformats-officedocument.wordprocessingml.document">DOCX</option>
              <option value="image/png">PNG</option>
              <option value="image/jpeg">JPG</option>
              <option value="image/gif">GIF</option>
              <option value="image/bmp">BMP</option>
            </select>
            <select className="input" value={sortBy} onChange={(event) => setSortBy(event.target.value)}>
              <option value="updated">Recently updated</option>
              <option value="name">Name</option>
              <option value="size">Largest logical size</option>
            </select>
            <button className="button-secondary" type="submit">
              Apply
            </button>
          </form>

          {uploading || processingUpload ? (
            <div className="mt-5">
              <div className="h-2 rounded-full bg-slate-100">
                <div className="h-2 rounded-full bg-brand-600 transition-all" style={{ width: `${uploading ? uploadProgress : 100}%` }} />
              </div>
              <p className="mt-2 text-sm text-slate-500">
                {uploading ? uploadProgressLabel(uploadProgress) : processingUpload?.message || "Processing upload..."}
              </p>
            </div>
          ) : null}

          {!uploading && !processingUpload ? <p className="mt-5 text-sm text-slate-500">{UPLOAD_SUPPORT_MESSAGE}</p> : null}

          {error ? <p className="mt-5 rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p> : null}
        </div>

        <div className="grid gap-4 sm:grid-cols-3 xl:grid-cols-3">
          <div className="card">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="section-label">Documents</p>
                <p className="mt-2 text-3xl font-semibold tracking-[-0.04em] text-slate-950">{formatCount(visibleDocuments.length)}</p>
              </div>
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand-50 text-brand-700">
                <AppIcon className="h-4.5 w-4.5" name="files" />
              </span>
            </div>
          </div>
          <div className="card">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="section-label">Editable</p>
                <p className="mt-2 text-3xl font-semibold tracking-[-0.04em] text-slate-950">{formatCount(editableCount)}</p>
              </div>
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-700">
                <AppIcon className="h-4.5 w-4.5" name="check" />
              </span>
            </div>
          </div>
          <div className="card sm:col-span-3 xl:col-span-1">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="section-label">Saved</p>
                <p className="mt-2 text-3xl font-semibold tracking-[-0.04em] text-slate-950">{formatBytes(totalSaved)}</p>
              </div>
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-amber-50 text-amber-700">
                <AppIcon className="h-4.5 w-4.5" name="storage" />
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="mt-6 space-y-4 lg:hidden">
        {loading ? (
          <div className="card text-sm text-slate-500">Loading documents...</div>
        ) : visibleDocuments.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-icon">
                <AppIcon className="h-5 w-5" name="files" />
              </div>
              <p className="mt-4 text-base font-medium text-slate-900">No matching documents</p>
            <p className="mt-2 text-sm text-slate-500">Try a different search or clear the filters.</p>
            </div>
          ) : (
          visibleDocuments.map((document) => (
            <div key={document.id} className="card">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-3">
                    <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                      <AppIcon className="h-4.5 w-4.5" name="files" />
                    </span>
                    <div className="min-w-0">
                      <p className="truncate font-medium text-slate-950">{document.name}</p>
                      <div className="mt-2 flex flex-wrap gap-2">
                        <span className="label-chip">{documentTypeLabel(document.mediaType)}</span>
                        <span className={`label-chip border-transparent ${permissionTone(document.permission)}`}>{document.permission}</span>
                        <span className={`label-chip border-transparent ${processingTone(document.processingStatus)}`}>{processingLabel(document)}</span>
                      </div>
                      {document.searchSnippet ? (
                        <p className="mt-3 text-sm leading-6 text-slate-600">{renderHighlightedSnippet(document.searchSnippet, query)}</p>
                      ) : null}
                    </div>
                  </div>
                </div>

                <div className="flex shrink-0 flex-col gap-2">
                  {document.readyForViewing ? (
                    <button className="button-secondary" onClick={() => handlePrimaryDocumentAction(document)} type="button">
                      {openActionLabel}
                    </button>
                  ) : (
                    <button className="button-secondary" disabled type="button">
                      {document.processingStatus === "FAILED" ? "Unavailable" : "Processing"}
                    </button>
                  )}
                  <Link className="button-secondary" to={`/files/${document.id}`}>
                    Details
                  </Link>
                </div>
              </div>

              <div className="mt-5 grid gap-3 sm:grid-cols-2">
                <div className="surface-panel text-sm">
                  <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Owner</p>
                  <p className="mt-1 font-medium text-slate-900">{document.ownerName}</p>
                </div>
                <div className="surface-panel text-sm">
                  <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Updated</p>
                  <p className="mt-1 font-medium text-slate-900">{formatDateTime(document.updatedAt)}</p>
                </div>
                <div className="surface-panel text-sm">
                  <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Logical</p>
                  <p className="mt-1 font-medium text-slate-900">{formatBytes(document.logicalSize)}</p>
                </div>
                <div className="surface-panel text-sm">
                  <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Actual</p>
                  <p className="mt-1 font-medium text-slate-900">{formatBytes(document.actualStoredSize)}</p>
                </div>
                <div className="rounded-2xl bg-emerald-50 px-4 py-4 text-sm">
                  <p className="text-xs uppercase tracking-[0.15em] text-emerald-600">Saved</p>
                  <p className="mt-1 font-medium text-emerald-700">{formatBytes(document.dedupSavedSize)}</p>
                </div>
                <div className="rounded-2xl bg-slate-50 px-4 py-4 text-sm">
                  <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Dedup Ratio</p>
                  <p className="mt-1 font-medium text-slate-900">{formatPercent(document.dedupRatio)}</p>
                </div>
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                <span className="label-chip">{formatCount(document.totalVersions)} versions</span>
                <span className="label-chip">{formatCount(document.totalChunkReferences)} chunks</span>
                <span className="label-chip">{document.summary ? "Summary saved" : "No summary"}</span>
              </div>
            </div>
          ))
        )}
      </div>

      <div className="mt-6 hidden card overflow-hidden p-0 lg:block">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50/90">
              <tr>
                {["Document", "Owner", "Access", "Versions", "Storage", "Updated", "Action"].map((heading) => (
                  <th key={heading} className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-[0.15em] text-slate-500">
                    {heading}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 bg-white">
              {loading ? (
                <tr>
                  <td className="px-6 py-8 text-sm text-slate-500" colSpan={7}>
                    Loading documents...
                  </td>
                </tr>
              ) : visibleDocuments.length === 0 ? (
                <tr>
                  <td className="px-6 py-8 text-sm text-slate-500" colSpan={7}>
                    No documents match the current filters.
                  </td>
                </tr>
              ) : (
                visibleDocuments.map((document) => (
                  <tr key={document.id} className="transition hover:bg-slate-50/70">
                    <td className="px-6 py-4">
                      <div className="flex items-start gap-3">
                        <span className="mt-0.5 flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                          <AppIcon className="h-4.5 w-4.5" name="files" />
                        </span>
                        <div className="min-w-0">
                          <p className="max-w-[320px] truncate font-medium text-slate-950">{document.name}</p>
                      <div className="mt-2 flex flex-wrap gap-2">
                        <span className="label-chip">{documentTypeLabel(document.mediaType)}</span>
                        <span className={`label-chip border-transparent ${processingTone(document.processingStatus)}`}>{processingLabel(document)}</span>
                        {document.summary ? <span className="label-chip">Summary saved</span> : null}
                        {(document.tags || []).length ? <span className="label-chip">{formatCount(document.tags.length)} tags</span> : null}
                      </div>
                          {document.searchSnippet ? (
                            <p className="mt-3 max-w-[360px] text-sm leading-6 text-slate-600">{renderHighlightedSnippet(document.searchSnippet, query)}</p>
                          ) : null}
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-600">{document.ownerName}</td>
                    <td className="px-6 py-4">
                      <span className={`rounded-full px-3 py-1 text-xs font-medium ${permissionTone(document.permission)}`}>{document.permission}</span>
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-600">
                      <p className="font-medium text-slate-900">{formatCount(document.totalVersions)}</p>
                      <p className="mt-1 text-xs text-slate-400">{formatPercent(document.dedupRatio)} saved</p>
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-600">
                      <p className="font-medium text-slate-900">{formatBytes(document.logicalSize)}</p>
                      <p className="mt-1 text-xs text-slate-400">{formatBytes(document.actualStoredSize)} actual</p>
                      <p className="mt-1 text-xs text-emerald-700">Saved {formatBytes(document.dedupSavedSize)}</p>
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-600">{formatDateTime(document.updatedAt)}</td>
                    <td className="px-6 py-4 text-sm">
                      <div className="flex flex-wrap gap-2">
                        {document.readyForViewing ? (
                          <button className="button-secondary" onClick={() => handlePrimaryDocumentAction(document)} type="button">
                            {openActionLabel}
                          </button>
                        ) : (
                          <button className="button-secondary" disabled type="button">
                            {document.processingStatus === "FAILED" ? "Unavailable" : "Processing"}
                          </button>
                        )}
                        <Link className="button-secondary" to={`/files/${document.id}`}>
                          Details
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default MyFilesPage;
