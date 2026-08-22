import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useConfirm } from "../app/providers/ConfirmProvider";
import { usePreferences } from "../app/providers/PreferencesProvider";
import { useToast } from "../app/providers/ToastProvider";
import DocumentPreviewPanel from "../components/documents/DocumentPreviewPanel";
import ActivityTimeline from "../components/dashboard/ActivityTimeline";
import PageHeader from "../components/PageHeader";
import AppIcon from "../components/ui/AppIcon";
import { MAX_UPLOAD_BYTES, SUPPORTED_UPLOAD_ACCEPT, UPLOAD_SUPPORT_MESSAGE } from "../constants/uploads";
import {
  addDocumentComment,
  askDocument,
  deleteDocument,
  disableShareLink,
  downloadDocument,
  downloadVersion,
  enableShareLink,
  generateSummary,
  generateTags,
  getDocument,
  getDocumentViewer,
  listVersions,
  removeCollaborator,
  renameDocument,
  retryDocumentProcessing,
  rewriteDocument,
  shareDocument,
  waitForDocumentProcessing,
  updateCollaborator,
  uploadVersion,
} from "../services/documentService";
import { getApiErrorMessage } from "../utils/apiError";
import { formatBytes, formatBytesDetailed, formatCount, formatDateTime, formatPercent } from "../utils/formatters";
import { documentTypeLabel, formatVersionOrigin } from "../utils/productPresentation";
import { notifyWorkspaceUpdated, subscribeWorkspaceUpdates } from "../utils/workspaceEvents";

const initialBusyState = {
  rename: false,
  downloadCurrent: false,
  delete: false,
  uploadVersion: false,
  summary: false,
  tags: false,
  ask: false,
  rewritePreview: false,
  rewriteSave: false,
  comment: false,
  retryProcessing: false,
  share: false,
  toggleLink: false,
  collaboratorId: null,
};

const detailTabs = [
  { id: "viewer", label: "Viewer", icon: "eye", helper: "Open and review the current document." },
  { id: "versions", label: "Versions", icon: "restore", helper: "Trace history and download revisions." },
  { id: "activity", label: "Activity", icon: "activity", helper: "Review access, AI, and version events." },
  { id: "ai", label: "AI", icon: "spark", helper: "Generate metadata and rewrite content." },
  { id: "collaboration", label: "Collaboration", icon: "share", helper: "Manage members and public access." },
];

const summaryDepthOptions = [
  { id: "concise", label: "Short", helper: "2 lines", sentences: 2 },
  { id: "standard", label: "Standard", helper: "Balanced", sentences: 3 },
  { id: "detailed", label: "Detailed", helper: "More context", sentences: 5 },
  { id: "bullet", label: "Bullet", helper: "Summary with bullet highlights", sentences: 4 },
];

const rewriteModeOptions = [
  { id: "professional", label: "Professional", helper: "Clear and polished" },
  { id: "formal", label: "Formal", helper: "Academic or executive tone" },
  { id: "simplify", label: "Simplify", helper: "Plain language" },
  { id: "readability", label: "Readability", helper: "Smoother flow and clarity" },
  { id: "shorten", label: "Shorten", helper: "Condensed copy" },
  { id: "key-points", label: "Key Points", helper: "Bullet summary" },
];

const shareExpiryOptions = [
  { id: "never", label: "No expiry", value: null },
  { id: "1", label: "1 day", value: 1 },
  { id: "7", label: "7 days", value: 7 },
  { id: "30", label: "30 days", value: 30 },
];

function normalizeTabId(value) {
  return detailTabs.some((tab) => tab.id === value) ? value : "viewer";
}

function statusToneClasses(tone) {
  if (tone === "error") return "bg-rose-50 text-rose-700";
  if (tone === "warning") return "bg-amber-50 text-amber-700";
  return "bg-emerald-50 text-emerald-700";
}

function permissionTone(permission) {
  if (permission === "EDITOR" || permission === "OWNER") {
    return "bg-emerald-50 text-emerald-700";
  }
  return "bg-slate-100 text-slate-600";
}

function previewLooksLikeBulletList(value) {
  return value
    .split("\n")
    .filter(Boolean)
    .every((line) => line.trim().startsWith("- "));
}

function renderSimpleList(items) {
  if (!items?.length) {
    return <p className="text-sm text-slate-500">Nothing available yet.</p>;
  }

  return (
    <ul className="space-y-2 text-sm leading-7 text-slate-700">
      {items.map((item) => (
        <li key={item} className="flex gap-3">
          <span className="mt-[0.58rem] h-1.5 w-1.5 rounded-full bg-slate-400" />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}

function versionUploadProgressLabel(progress) {
  if (progress >= 100) {
    return "Upload complete. Processing started...";
  }
  if (progress > 0) {
    return `Uploading ${progress}%`;
  }
  return "Preparing upload...";
}

function summaryPreferenceToDepth(value) {
  if (value === "SHORT") return "concise";
  if (value === "BULLET") return "bullet";
  return "detailed";
}

function rewritePreferenceToMode(value) {
  if (value === "FORMAL") return "formal";
  if (value === "SIMPLE") return "simplify";
  return "professional";
}

function shareExpiryPreferenceToOption(value) {
  if (value == null) {
    return "never";
  }
  return String(value);
}

function SectionHeading({ icon, label, description, action }) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-3">
        <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
          <AppIcon className="h-4.5 w-4.5" name={icon} />
        </span>
        <div>
          <p className="section-label">{label}</p>
          <p className="mt-2 text-sm text-slate-500">{description}</p>
        </div>
      </div>
      {action ? <div className="w-full sm:w-auto">{action}</div> : null}
    </div>
  );
}

function MetricTile({ label, value, tone = "default" }) {
  const toneClass =
    tone === "positive"
      ? "border-emerald-200 bg-emerald-50"
      : tone === "hero"
        ? "border-white/10 bg-white/8 text-white"
        : "surface-panel";

  const labelClass = tone === "positive" ? "text-emerald-600" : tone === "hero" ? "text-slate-300" : "text-slate-500";
  const valueClass = tone === "positive" ? "text-emerald-700" : tone === "hero" ? "text-white" : "text-slate-950";

  return (
    <div className={`rounded-[24px] border px-4 py-4 ${toneClass}`}>
      <p className={`text-xs uppercase tracking-[0.15em] ${labelClass}`}>{label}</p>
      <p className={`mt-2 text-base font-semibold ${valueClass}`}>{value}</p>
    </div>
  );
}

function EmptyPanel({ icon, title, description, action }) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon">
        <AppIcon className="h-5 w-5" name={icon} />
      </div>
      <p className="mt-4 text-base font-medium text-slate-900">{title}</p>
      <p className="mt-2 text-sm text-slate-500">{description}</p>
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}

function FileDetailsPage() {
  const { fileId } = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { showToast } = useToast();
  const { preferences } = usePreferences();
  const { confirm } = useConfirm();
  const [document, setDocument] = useState(null);
  const [viewer, setViewer] = useState(null);
  const [versions, setVersions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [viewerLoading, setViewerLoading] = useState(true);
  const [error, setError] = useState("");
  const [viewerError, setViewerError] = useState("");
  const [renameValue, setRenameValue] = useState("");
  const [summaryDepth, setSummaryDepth] = useState("standard");
  const [rewriteMode, setRewriteMode] = useState("professional");
  const [rewritePreview, setRewritePreview] = useState("");
  const [questionText, setQuestionText] = useState("");
  const [questionResponse, setQuestionResponse] = useState(null);
  const [shareEmail, setShareEmail] = useState("");
  const [sharePermission, setSharePermission] = useState("VIEWER");
  const [shareExpiryDays, setShareExpiryDays] = useState("never");
  const [copyMessage, setCopyMessage] = useState("");
  const [commentText, setCommentText] = useState("");
  const [commentVersionId, setCommentVersionId] = useState("");
  const [aiStatus, setAiStatus] = useState(null);
  const [busy, setBusy] = useState(initialBusyState);
  const [versionUploadProgress, setVersionUploadProgress] = useState(0);
  const [downloadingVersionId, setDownloadingVersionId] = useState(null);
  const [activeTab, setActiveTab] = useState(() => normalizeTabId(searchParams.get("tab")));

  function setBusyState(key, value) {
    setBusy((current) => ({ ...current, [key]: value }));
  }

  function handleTabChange(nextTab) {
    const normalizedTab = normalizeTabId(nextTab);
    setActiveTab(normalizedTab);

    const nextSearchParams = new URLSearchParams(searchParams);
    if (normalizedTab === "viewer") {
      nextSearchParams.delete("tab");
    } else {
      nextSearchParams.set("tab", normalizedTab);
    }
    setSearchParams(nextSearchParams, { replace: true });
  }

  function loadDocument(showLoader = true) {
    if (showLoader) {
      setLoading(true);
    }
    setError("");
    Promise.all([getDocument(fileId), listVersions(fileId)])
      .then(([documentResponse, versionList]) => {
        setDocument(documentResponse);
        setVersions(versionList);
        setRenameValue(documentResponse.name);
      })
      .catch((requestError) => setError(getApiErrorMessage(requestError, "Unable to load document")))
      .finally(() => {
        if (showLoader) {
          setLoading(false);
        }
      });
  }

  function loadViewer(showLoader = true) {
    if (showLoader) {
      setViewerLoading(true);
    }
    setViewerError("");
    getDocumentViewer(fileId)
      .then(setViewer)
      .catch((requestError) => setViewerError(getApiErrorMessage(requestError, "Unable to open the document preview")))
      .finally(() => {
        if (showLoader) {
          setViewerLoading(false);
        }
      });
  }

  useEffect(() => {
    setRewritePreview("");
    setQuestionResponse(null);
    setAiStatus(null);
    setCopyMessage("");
    setActiveTab(normalizeTabId(searchParams.get("tab")));
    loadDocument(true);
  }, [fileId]);

  useEffect(() => {
    setActiveTab(normalizeTabId(searchParams.get("tab")));
  }, [searchParams]);

  useEffect(() => {
    setSummaryDepth(summaryPreferenceToDepth(preferences.summaryLength));
    setRewriteMode(rewritePreferenceToMode(preferences.rewriteMode));
    setShareExpiryDays(shareExpiryPreferenceToOption(preferences.defaultShareExpiryDays));
  }, [preferences.defaultShareExpiryDays, preferences.rewriteMode, preferences.summaryLength]);

  useEffect(() => {
    const unsubscribe = subscribeWorkspaceUpdates((event) => {
      if (!event.detail?.documentId || event.detail.documentId === fileId) {
        loadDocument(false);
      }
    });

    return unsubscribe;
  }, [fileId]);

  useEffect(() => {
    if (!document) {
      return;
    }
    if (!document.readyForViewing) {
      setViewer(null);
      setViewerLoading(false);
      setViewerError(
        document.processingStatus === "FAILED"
          ? document.processingMessage || "This upload failed during processing."
          : document.processingMessage || "This document is still processing."
      );
      return;
    }
    loadViewer(true);
  }, [fileId, document?.readyForViewing, document?.currentVersion?.id, document?.latestVersion?.id]);

  const selectedRewriteMode = useMemo(
    () => rewriteModeOptions.find((option) => option.id === rewriteMode) || rewriteModeOptions[0],
    [rewriteMode],
  );
  const selectedShareExpiryOption = useMemo(
    () => shareExpiryOptions.find((option) => option.id === shareExpiryDays) || shareExpiryOptions[0],
    [shareExpiryDays],
  );
  const currentVersion = document?.currentVersion || document?.latestVersion;
  const readyForViewing = Boolean(document?.readyForViewing);
  const recentVersions = versions.slice(0, 5);

  async function handleDownloadCurrent() {
    if (!readyForViewing) {
      const message = document?.processingMessage || "This document is still processing.";
      setError(message);
      showToast({ title: "Download unavailable", description: message, tone: "warning" });
      return;
    }
    setBusyState("downloadCurrent", true);
    try {
      await downloadDocument(fileId, currentVersion?.fileName || document?.name || "document");
      showToast({
        title: "Download started",
        description: "The latest version is being prepared.",
      });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to download document");
      setError(message);
      showToast({ title: "Download failed", description: message, tone: "error" });
    } finally {
      setBusyState("downloadCurrent", false);
    }
  }

  async function handleDownloadVersion(version) {
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

  async function handleRename() {
    if (!document?.canEdit || !renameValue.trim()) return;
    setBusyState("rename", true);
    try {
      const updated = await renameDocument(fileId, renameValue.trim());
      setDocument(updated);
      notifyWorkspaceUpdated({ type: "document-renamed", documentId: fileId });
      showToast({
        title: "Document renamed",
        description: "The new name has been saved.",
      });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to rename document");
      setError(message);
      showToast({ title: "Rename failed", description: message, tone: "error" });
    } finally {
      setBusyState("rename", false);
    }
  }

  async function handleDelete() {
    if (!document?.canManageAccess) return;
    const confirmed = await confirm({
      eyebrow: "Delete document",
      title: "Delete this document?",
      description: "This will permanently remove the document, every stored version, and all share access.",
      confirmLabel: "Delete document",
      destructive: true,
    });

    if (!confirmed) return;

    setBusyState("delete", true);
    try {
      await deleteDocument(fileId);
      notifyWorkspaceUpdated({ type: "document-deleted", documentId: fileId });
      showToast({
        title: "Document deleted",
        description: "The document and its versions were removed.",
      });
      navigate("/files");
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to delete document");
      setError(message);
      showToast({ title: "Delete failed", description: message, tone: "error" });
    } finally {
      setBusyState("delete", false);
    }
  }

  async function handleVersionUpload(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (Number.isFinite(MAX_UPLOAD_BYTES) && file.size > MAX_UPLOAD_BYTES) {
      const message = "Choose a smaller file for upload.";
      setError(message);
      showToast({ title: "Upload blocked", description: message, tone: "error" });
      event.target.value = "";
      return;
    }
    setBusyState("uploadVersion", true);
    setVersionUploadProgress(0);
    try {
      const response = await uploadVersion(fileId, file, setVersionUploadProgress);
      setBusyState("uploadVersion", false);
      setVersionUploadProgress(100);
      loadDocument(false);
      showToast({
        title: "Version received",
        description: response.message,
      });
      const finalStatus = await waitForDocumentProcessing(fileId, {
        onUpdate: () => loadDocument(false),
      });
      if (finalStatus.status !== "COMPLETED") {
        const message = finalStatus.message || "The version could not be processed.";
        setError(message);
        if (preferences.notifyProcessingFailed) {
          showToast({ title: "Processing failed", description: message, tone: "error" });
        }
        return;
      }

      notifyWorkspaceUpdated({ type: "version-uploaded", documentId: fileId });
      if (preferences.notifyVersionCreated) {
        showToast({
          title: "Version ready",
          description: finalStatus.message || "The latest version is ready.",
          tone: finalStatus.totalVersions > versions.length ? "success" : "warning",
        });
      }
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to upload new version");
      setError(message);
      showToast({ title: "Version upload failed", description: message, tone: "error" });
    } finally {
      setBusyState("uploadVersion", false);
      setVersionUploadProgress(0);
      loadDocument(false);
      event.target.value = "";
    }
  }

  async function handleSummary() {
    if (!document?.canEdit) return;
    const selectedSummaryDepth = summaryDepthOptions.find((option) => option.id === summaryDepth) || summaryDepthOptions[1];
    setAiStatus({ tone: "warning", text: `Creating ${selectedSummaryDepth.label.toLowerCase()} summary...` });
    setBusyState("summary", true);
    try {
      await generateSummary(fileId, selectedSummaryDepth.sentences);
      notifyWorkspaceUpdated({ type: "ai-summary", documentId: fileId });
      setAiStatus({ tone: "success", text: `${selectedSummaryDepth.label} summary is ready.` });
      showToast({
        title: "Summary ready",
        description: `${selectedSummaryDepth.label} summary added to the latest version.`,
      });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to generate summary");
      setError(message);
      setAiStatus({ tone: "error", text: message });
      showToast({ title: "Summary failed", description: message, tone: "error" });
    } finally {
      setBusyState("summary", false);
    }
  }

  async function handleTags() {
    if (!document?.canEdit) return;
    setAiStatus({ tone: "warning", text: "Creating tags..." });
    setBusyState("tags", true);
    try {
      await generateTags(fileId);
      notifyWorkspaceUpdated({ type: "ai-tags", documentId: fileId });
      setAiStatus({ tone: "success", text: "Tags are ready." });
      showToast({ title: "Tags ready", description: "The latest version now includes updated tags." });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to generate tags");
      setError(message);
      setAiStatus({ tone: "error", text: message });
      showToast({ title: "Tag generation failed", description: message, tone: "error" });
    } finally {
      setBusyState("tags", false);
    }
  }

  async function handleAskDocument() {
    const question = questionText.trim();
    if (!question) {
      showToast({
        title: "Ask a question",
        description: "Type a question before sending it to the document assistant.",
        tone: "warning",
      });
      return;
    }

    setBusyState("ask", true);
    try {
      const response = await askDocument(fileId, { question });
      setQuestionResponse(response);
      showToast({
        title: response.answered ? "Answer ready" : "No clear answer found",
        description: response.answered
          ? "The answer is grounded in the current document text."
          : "The assistant could not find enough evidence in the document.",
        tone: response.answered ? "success" : "warning",
      });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to answer this question");
      setError(message);
      showToast({ title: "Question failed", description: message, tone: "error" });
    } finally {
      setBusyState("ask", false);
    }
  }

  async function handleRewrite(applyAsNewVersion) {
    if (!document?.canEdit) return;
    const busyKey = applyAsNewVersion ? "rewriteSave" : "rewritePreview";
    setBusyState(busyKey, true);
    setAiStatus({
      tone: "warning",
      text: applyAsNewVersion
        ? `Saving ${selectedRewriteMode.label.toLowerCase()} rewrite...`
        : `Preparing ${selectedRewriteMode.label.toLowerCase()} preview...`,
    });
    try {
      const response = await rewriteDocument(fileId, { mode: rewriteMode, applyAsNewVersion });
      setRewritePreview(response.rewrittenText);
      if (applyAsNewVersion) {
        if (response.versionCreated) {
          notifyWorkspaceUpdated({ type: "ai-rewrite-version", documentId: fileId });
          if (preferences.notifyVersionCreated) {
            showToast({
              title: "AI rewrite saved",
              description: `Saved as version ${response.versionNumber}.`,
            });
          }
          setAiStatus({ tone: "success", text: `${selectedRewriteMode.label} rewrite saved as a new version.` });
        } else {
          showToast({
            title: "No new version created",
            description: "The rewrite matches the current content.",
            tone: "warning",
          });
          setAiStatus({ tone: "warning", text: "The rewrite matches the current version." });
        }
      } else {
        showToast({
          title: "Rewrite preview ready",
          description: "Review the latest output before saving.",
        });
        setAiStatus({ tone: "success", text: "Rewrite preview is ready." });
      }
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to rewrite document");
      setError(message);
      setAiStatus({ tone: "error", text: message });
      showToast({ title: "Rewrite failed", description: message, tone: "error" });
    } finally {
      setBusyState(busyKey, false);
    }
  }

  async function handleRetryProcessing() {
    setBusyState("retryProcessing", true);
    try {
      const response = await retryDocumentProcessing(fileId);
      setError("");
      setQuestionResponse(null);
      showToast({
        title: "Retry started",
        description: response.message || "The failed upload is being processed again.",
      });
      loadDocument(false);
      const finalStatus = await waitForDocumentProcessing(fileId, {
        onUpdate: () => loadDocument(false),
      });
      if (finalStatus.status === "COMPLETED") {
        notifyWorkspaceUpdated({ type: "processing-retried", documentId: fileId });
        if (preferences.notifyUploadCompleted) {
          showToast({
            title: "Document ready",
            description: finalStatus.message || "Processing finished successfully.",
          });
        }
      } else {
        const message = finalStatus.message || "Processing failed again.";
        setError(message);
        if (preferences.notifyProcessingFailed) {
          showToast({ title: "Processing failed", description: message, tone: "error" });
        }
      }
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to retry processing");
      setError(message);
      showToast({ title: "Retry failed", description: message, tone: "error" });
      loadDocument(false);
    } finally {
      setBusyState("retryProcessing", false);
    }
  }

  async function handleShare(event) {
    event.preventDefault();
    if (!shareEmail.trim()) return;
    setBusyState("share", true);
    try {
      await shareDocument(fileId, { email: shareEmail.trim(), permission: sharePermission });
      setShareEmail("");
      notifyWorkspaceUpdated({ type: "document-shared", documentId: fileId });
      showToast({
        title: "Collaborator added",
        description: "Access has been shared.",
      });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to share document");
      setError(message);
      showToast({ title: "Share failed", description: message, tone: "error" });
    } finally {
      setBusyState("share", false);
    }
  }

  async function handleCollaboratorPermission(collaboratorId, permission) {
    setBusy((current) => ({ ...current, collaboratorId }));
    try {
      await updateCollaborator(fileId, collaboratorId, { permission });
      notifyWorkspaceUpdated({ type: "share-updated", documentId: fileId });
      showToast({
        title: "Permission updated",
        description: "Access has been updated.",
      });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to update collaborator permission");
      setError(message);
      showToast({ title: "Permission update failed", description: message, tone: "error" });
    } finally {
      setBusy((current) => ({ ...current, collaboratorId: null }));
    }
  }

  async function handleRemoveCollaborator(collaboratorId) {
    const confirmed = await confirm({
      eyebrow: "Remove collaborator",
      title: "Remove collaborator access?",
      description: "The selected user will lose access to this document immediately.",
      confirmLabel: "Remove access",
      destructive: true,
    });

    if (!confirmed) return;

    setBusy((current) => ({ ...current, collaboratorId }));
    try {
      await removeCollaborator(fileId, collaboratorId);
      notifyWorkspaceUpdated({ type: "share-removed", documentId: fileId });
      showToast({
        title: "Collaborator removed",
        description: "Access has been removed.",
      });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to remove collaborator");
      setError(message);
      showToast({ title: "Remove failed", description: message, tone: "error" });
    } finally {
      setBusy((current) => ({ ...current, collaboratorId: null }));
    }
  }

  async function handleToggleShareLink() {
    setBusyState("toggleLink", true);
    try {
      if (document?.publicShareUrl) {
        await disableShareLink(fileId);
        showToast({
          title: "Public link disabled",
          description: "The read-only link is no longer active.",
        });
      } else {
        await enableShareLink(fileId, { expiresInDays: selectedShareExpiryOption.value });
        showToast({
          title: "Public link enabled",
          description: selectedShareExpiryOption.value
            ? `Anyone with the link can open the latest version for ${selectedShareExpiryOption.label.toLowerCase()}.`
            : "Anyone with the link can open the latest version.",
        });
      }
      notifyWorkspaceUpdated({ type: "share-link-changed", documentId: fileId });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to update public share link");
      setError(message);
      showToast({ title: "Public link update failed", description: message, tone: "error" });
    } finally {
      setBusyState("toggleLink", false);
    }
  }

  async function handleAddComment() {
    const content = commentText.trim();
    if (!content) {
      showToast({
        title: "Add a review note",
        description: "Write a short note before saving it.",
        tone: "warning",
      });
      return;
    }

    setBusyState("comment", true);
    try {
      await addDocumentComment(fileId, {
        content,
        versionId: commentVersionId || null,
      });
      setCommentText("");
      setCommentVersionId("");
      loadDocument(false);
      notifyWorkspaceUpdated({ type: "review-note-added", documentId: fileId });
      showToast({
        title: "Review note added",
        description: "The note is now part of the document history.",
      });
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, "Unable to save review note");
      setError(message);
      showToast({ title: "Review note failed", description: message, tone: "error" });
    } finally {
      setBusyState("comment", false);
    }
  }

  async function handleCopyLink() {
    if (!document?.publicShareUrl) return;
    try {
      await navigator.clipboard.writeText(document.publicShareUrl);
      setCopyMessage("Public link copied");
      showToast({
        title: "Link copied",
        description: "The public share URL is in your clipboard.",
      });
      window.setTimeout(() => setCopyMessage(""), 2000);
    } catch {
      setCopyMessage("Copy failed");
      showToast({
        title: "Copy failed",
        description: "The browser blocked clipboard access.",
        tone: "error",
      });
      window.setTimeout(() => setCopyMessage(""), 2000);
    }
  }

  function renderRewritePreview() {
    if (!rewritePreview) {
      return <p className="text-sm text-slate-500">No rewrite preview yet.</p>;
    }

    if (previewLooksLikeBulletList(rewritePreview)) {
      return (
        <ul className="space-y-2 text-sm leading-7 text-slate-700">
          {rewritePreview
            .split("\n")
            .filter(Boolean)
            .map((line) => (
              <li key={line} className="flex gap-3">
                <span className="mt-[0.58rem] h-1.5 w-1.5 rounded-full bg-slate-400" />
                <span>{line.replace(/^- /, "")}</span>
              </li>
            ))}
        </ul>
      );
    }

    return <p className="whitespace-pre-wrap text-sm leading-7 text-slate-700">{rewritePreview}</p>;
  }

  function renderCommentScope(comment) {
    if (!comment?.versionNumber) {
      return "Document note";
    }
    return `Version ${comment.versionNumber}`;
  }

  function renderStoragePanel() {
    return (
      <section className="card">
        <SectionHeading icon="storage" label="Storage" description="See how this document is stored and how much space it saves." />

        <div className="mt-6 grid gap-3 sm:grid-cols-2">
          <MetricTile label="Logical" value={formatBytesDetailed(document.storage.logicalSize)} />
          <MetricTile label="Actual" value={formatBytesDetailed(document.storage.actualStoredSize)} />
          <MetricTile label="Saved" value={formatBytesDetailed(document.storage.dedupSavedSize)} tone="positive" />
          <MetricTile label="Dedup Ratio" value={formatPercent(document.storage.dedupRatio)} />
          <MetricTile label="Avg Chunk" value={formatBytes(document.storage.averageChunkSizeBytes)} />
          <MetricTile label="Compression" value={formatPercent(1 - document.storage.compressionRatio)} />
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <span className="label-chip">{formatCount(document.storage.totalChunkReferences)} chunks</span>
          <span className="label-chip">{formatCount(document.storage.uniqueChunkCount)} unique</span>
          <span className="label-chip">{formatCount(document.storage.reusedChunkReferences)} reused</span>
          <span className="label-chip">{formatCount(document.totalVersions)} versions</span>
        </div>
      </section>
    );
  }

  function renderCurrentVersionPanel() {
    return (
      <section className="card">
        <SectionHeading icon="restore" label="Current Version" description="Details for the version that is currently live." />

        <div className="mt-6 grid gap-3 sm:grid-cols-2">
          <MetricTile label="Version" value={`Version ${currentVersion?.versionNumber || 1}`} />
          <MetricTile label="Type" value={documentTypeLabel(currentVersion?.mediaType)} />
          <MetricTile label="Logical" value={formatBytesDetailed(currentVersion?.logicalSize)} />
          <MetricTile label="Actual" value={formatBytesDetailed(currentVersion?.actualStoredSize)} />
          <MetricTile label="Saved" value={formatBytesDetailed(currentVersion?.dedupSavedSize)} tone="positive" />
          <MetricTile label="Dedup Ratio" value={formatPercent(currentVersion?.dedupRatio)} />
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <span className="label-chip">{formatCount(currentVersion?.totalChunkCount)} chunks</span>
          <span className="label-chip">{formatCount(currentVersion?.newChunkCount)} new</span>
          <span className="label-chip">{formatCount(currentVersion?.reusedChunkCount)} reused</span>
          {currentVersion?.ocrApplied ? <span className="label-chip">OCR assisted</span> : null}
        </div>

        <div className="mt-5 rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4">
          <p className="font-medium text-slate-950">{currentVersion?.fileName}</p>
          <div className="mt-3 space-y-2 text-sm text-slate-600">
            <p>{formatDateTime(currentVersion?.createdAt)}</p>
            <p>{formatVersionOrigin(currentVersion?.origin)}</p>
            {currentVersion?.note ? <p className="text-slate-500">{currentVersion.note}</p> : null}
          </div>
        </div>

        <div className="mt-4 rounded-[24px] border border-slate-200 bg-slate-50 px-4 py-4">
          <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Recent Access</p>
          <p className="mt-3 text-sm text-slate-700">
            {document.lastViewedAt
              ? `${document.lastViewedByName || "Someone"} viewed this on ${formatDateTime(document.lastViewedAt)}`
              : "No view activity has been recorded yet."}
          </p>
        </div>

        {currentVersion?.ocrApplied ? (
          <div className="mt-4 rounded-[24px] border border-amber-200 bg-amber-50 px-4 py-4">
            <p className="text-xs uppercase tracking-[0.15em] text-amber-600">OCR Assistance</p>
            <p className="mt-3 text-sm text-amber-700">AI metadata for this version includes OCR-derived text from image-based content.</p>
          </div>
        ) : null}
      </section>
    );
  }

  function renderViewerTab() {
    const viewerAction = readyForViewing ? (
      <div className="flex flex-col gap-3 sm:flex-row">
        <Link className="button-primary w-full sm:w-auto" to={`/files/${fileId}/view`}>
          <AppIcon className="h-4 w-4" name="eye" />
          Open full viewer
        </Link>
        <Link className="button-secondary w-full sm:w-auto" to={`/files/${fileId}/versions`}>
          <AppIcon className="h-4 w-4" name="restore" />
          Version history
        </Link>
      </div>
    ) : (
      <Link className="button-secondary w-full sm:w-auto" to={`/files/${fileId}/versions`}>
        <AppIcon className="h-4 w-4" name="restore" />
        Version history
      </Link>
    );

    return (
      <div className="grid gap-6 xl:grid-cols-[1.08fr_0.92fr]">
        <section className="card">
          <SectionHeading
            icon="eye"
            label="Open Document"
            description="Read the current version here. Use version history to open an older revision."
            action={viewerAction}
          />

          <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
            <div>
              {!readyForViewing ? (
                <div className="rounded-[1.7rem] border border-amber-200 bg-amber-50 px-5 py-5 text-sm text-amber-700">
                  {document.processingMessage || "This document is still processing."}
                </div>
              ) : viewerLoading && !viewer ? (
                <div className="rounded-[1.7rem] border border-slate-200 bg-slate-50 px-5 py-10 text-sm text-slate-500">
                  Loading preview...
                </div>
              ) : viewerError ? (
                <div className="rounded-[1.7rem] border border-rose-200 bg-rose-50 px-5 py-5 text-sm text-rose-700">{viewerError}</div>
              ) : (
                <DocumentPreviewPanel compact fileId={fileId} viewer={viewer} />
              )}
            </div>

            <div className="space-y-4">
              <div className="surface-panel">
                <p className="section-label">Current Version</p>
                <div className="mt-4 space-y-3 text-sm text-slate-700">
                  <div>
                    <p className="text-xs uppercase tracking-[0.15em] text-slate-500">File</p>
                    <p className="mt-1 font-medium text-slate-900">{currentVersion?.fileName || document.name}</p>
                  </div>
                  <div>
                    <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Version</p>
                    <p className="mt-1 font-medium text-slate-900">Version {currentVersion?.versionNumber || "Pending"}</p>
                  </div>
                  <div>
                    <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Created</p>
                    <p className="mt-1 font-medium text-slate-900">{formatDateTime(currentVersion?.createdAt)}</p>
                  </div>
                  <div>
                    <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Access</p>
                    <p className="mt-1 font-medium text-slate-900">{document.permission}</p>
                  </div>
                </div>
              </div>

              <div className="surface-panel">
                <p className="section-label">Recent Access</p>
                <p className="mt-3 text-sm leading-7 text-slate-700">
                  {document.lastViewedAt
                    ? `${document.lastViewedByName || "Someone"} opened this file on ${formatDateTime(document.lastViewedAt)}.`
                    : "No one has opened this file in the viewer yet."}
                </p>
              </div>
            </div>
          </div>
        </section>

        <div className="space-y-6">
          {renderStoragePanel()}
          {renderCurrentVersionPanel()}
        </div>
      </div>
    );
  }

  function renderVersionsTab() {
    return (
      <div className="grid gap-6 xl:grid-cols-[1.08fr_0.92fr]">
        <section className="card">
          <SectionHeading
            icon="restore"
            label="Version Control"
            description="Open, download, and review recent revisions from one place."
            action={
              <Link className="button-secondary w-full sm:w-auto" to={`/files/${fileId}/versions`}>
                <AppIcon className="h-4 w-4" name="restore" />
                Open full compare view
              </Link>
            }
          />

          <div className="mt-6 space-y-4">
            {recentVersions.length ? (
              recentVersions.map((version) => (
                <div key={version.id} className="rounded-[26px] border border-slate-200 bg-white px-4 py-4">
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-lg font-semibold text-slate-950">Version {version.versionNumber}</p>
                        <span className="label-chip">{formatVersionOrigin(version.origin)}</span>
                        {version.currentVersion ? <span className="label-chip bg-emerald-50 text-emerald-700">Current</span> : null}
                        {version.ocrApplied ? <span className="label-chip">OCR assisted</span> : null}
                      </div>
                      <p className="mt-2 truncate text-sm font-medium text-slate-900">{version.fileName}</p>
                      <div className="mt-3 flex flex-wrap gap-2">
                        <span className="label-chip">{version.createdBy}</span>
                        <span className="label-chip">{formatDateTime(version.createdAt)}</span>
                        <span className="label-chip">{documentTypeLabel(version.mediaType)}</span>
                      </div>
                      {version.note ? <p className="mt-3 text-sm text-slate-500">{version.note}</p> : null}
                    </div>

                    <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                      <Link className="button-secondary" to={version.currentVersion ? `/files/${fileId}/view` : `/files/${fileId}/view?versionId=${version.id}`}>
                        <AppIcon className="h-4 w-4" name="eye" />
                        Open
                      </Link>
                      <button className="button-secondary" disabled={downloadingVersionId === version.id} onClick={() => handleDownloadVersion(version)} type="button">
                        <AppIcon className="h-4 w-4" name="download" />
                        {downloadingVersionId === version.id ? "Preparing..." : "Download"}
                      </button>
                    </div>
                  </div>

                  <div className="mt-5 grid gap-3 md:grid-cols-4">
                    <MetricTile label="Logical" value={formatBytesDetailed(version.logicalSize)} />
                    <MetricTile label="Actual" value={formatBytesDetailed(version.actualStoredSize)} />
                    <MetricTile label="Saved" value={formatBytesDetailed(version.dedupSavedSize)} tone="positive" />
                    <MetricTile label="Dedup Ratio" value={formatPercent(version.dedupRatio)} />
                  </div>
                </div>
              ))
            ) : (
              <EmptyPanel
                icon="restore"
                title="No versions available yet"
                description="Upload a document or save an AI rewrite to begin building a version trail."
              />
            )}
          </div>
        </section>

        <div className="space-y-6">
          {renderCurrentVersionPanel()}
          {renderStoragePanel()}
        </div>
      </div>
    );
  }

  function renderActivityTab() {
    return (
      <div className="grid gap-6 xl:grid-cols-[1.08fr_0.92fr]">
        <section className="card">
          <SectionHeading icon="activity" label="Document Activity" description="Access, version, sharing, and AI events for this document." />
          <div className="mt-6">
            <ActivityTimeline items={document.recentActivity} />
          </div>
        </section>

        <div className="space-y-6">
          <section className="card">
            <SectionHeading icon="comment" label="Review Notes" description="Capture short review notes for the document or a specific version." />

            <div className="mt-6 grid gap-3">
              <textarea
                className="input min-h-32 resize-y"
                disabled={busy.comment}
                placeholder="Add a short review note"
                value={commentText}
                onChange={(event) => setCommentText(event.target.value)}
              />
              <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
                <select className="input" disabled={busy.comment} value={commentVersionId} onChange={(event) => setCommentVersionId(event.target.value)}>
                  <option value="">Document note</option>
                  {versions.map((version) => (
                    <option key={version.id} value={version.id}>
                      {`Version ${version.versionNumber} · ${formatVersionOrigin(version.origin)}`}
                    </option>
                  ))}
                </select>
                <button className="button-primary" disabled={busy.comment} onClick={handleAddComment} type="button">
                  <AppIcon className="h-4 w-4" name="comment" />
                  {busy.comment ? "Saving..." : "Add note"}
                </button>
              </div>
            </div>

            <div className="mt-5 space-y-3">
              {document.comments?.length ? (
                document.comments.map((comment) => (
                  <div key={comment.id} className="surface-panel">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <p className="text-sm font-medium text-slate-900">{comment.authorName}</p>
                        <p className="mt-1 text-xs text-slate-500">{comment.authorEmail}</p>
                      </div>
                      <div className="text-right">
                        <span className="label-chip">{renderCommentScope(comment)}</span>
                        <p className="mt-2 text-xs text-slate-500">{formatDateTime(comment.createdAt)}</p>
                      </div>
                    </div>
                    <p className="mt-4 whitespace-pre-wrap text-sm leading-7 text-slate-700">{comment.content}</p>
                  </div>
                ))
              ) : (
                <EmptyPanel
                  icon="comment"
                  title="No review notes yet"
                  description="Use short notes to leave feedback without changing the file."
                />
              )}
            </div>
          </section>

          <section className="card">
            <SectionHeading icon="clock" label="Recent Access" description="Trace the latest review activity for this document." />
            <div className="mt-6 space-y-4">
              <div className="surface-panel">
                <p className="section-label">Last Viewed</p>
                <p className="mt-3 text-sm leading-7 text-slate-700">
                  {document.lastViewedAt
                    ? `${document.lastViewedByName || "Someone"} viewed this on ${formatDateTime(document.lastViewedAt)}`
                    : "No view activity has been recorded yet."}
                </p>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <MetricTile label="Versions" value={formatCount(document.totalVersions)} />
                <MetricTile label="Workspace Access" value={document.permission} />
              </div>
            </div>
          </section>

          {renderCurrentVersionPanel()}
        </div>
      </div>
    );
  }

  function renderAiTab() {
    return (
      <div className="grid gap-6 xl:grid-cols-[1.06fr_0.94fr]">
        <section className="card">
          <SectionHeading icon="spark" label="AI Workspace" description="Ask grounded questions, refresh metadata, and rewrite the latest document version." />

          <div className="mt-6 grid gap-6 2xl:grid-cols-[0.94fr_1.06fr]">
            <div>
              <div className="surface-panel">
                <p className="section-label">Ask the document</p>
                <p className="mt-3 text-sm text-slate-500">Use the extracted text already stored for this version. Answers stay grounded in document evidence.</p>
                <div className="mt-4 grid gap-3">
                  <textarea
                    className="input min-h-28 resize-y"
                    disabled={busy.ask || !readyForViewing}
                    placeholder="Ask about a deadline, decision, or named person"
                    value={questionText}
                    onChange={(event) => setQuestionText(event.target.value)}
                  />
                  <div className="flex flex-wrap gap-3">
                    <button className="button-secondary" disabled={busy.ask || !readyForViewing} onClick={handleAskDocument} type="button">
                      <AppIcon className="h-4 w-4" name="spark" />
                      {busy.ask ? "Checking..." : "Ask document"}
                    </button>
                  </div>
                </div>

                <div className="mt-5 rounded-[24px] border border-slate-200 bg-white px-4 py-4 shadow-sm">
                  <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Answer</p>
                  {questionResponse ? (
                    <div className="mt-3 space-y-3">
                      <p className="text-sm font-medium leading-7 text-slate-900">{questionResponse.answer}</p>
                      {questionResponse.evidenceSnippet ? (
                        <div className="rounded-2xl bg-slate-50 px-4 py-4 text-sm leading-7 text-slate-700">
                          <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Evidence</p>
                          <p className="mt-2">{questionResponse.evidenceSnippet}</p>
                        </div>
                      ) : null}
                    </div>
                  ) : (
                    <p className="mt-3 text-sm text-slate-500">Ask a question to get a short answer and a supporting snippet.</p>
                  )}
                </div>
              </div>

              <div className="flex flex-wrap gap-2">
                {summaryDepthOptions.map((option) => (
                  <button
                    key={option.id}
                    className={`rounded-2xl border px-4 py-3 text-left text-sm transition ${
                      summaryDepth === option.id
                        ? "border-brand-300 bg-brand-50 text-brand-700"
                        : "border-slate-200 bg-white text-slate-600 hover:border-brand-200 hover:bg-slate-50"
                    }`}
                    disabled={busy.summary || !document.canEdit}
                    onClick={() => setSummaryDepth(option.id)}
                    type="button"
                  >
                    <p className="font-medium">{option.label}</p>
                    <p className="mt-1 text-xs opacity-80">{option.helper}</p>
                  </button>
                ))}
              </div>

              <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                <button className="button-secondary" disabled={busy.summary || !document.canEdit} onClick={handleSummary} type="button">
                  <AppIcon className="h-4 w-4" name="summary" />
                  {busy.summary ? "Generating..." : "Generate summary"}
                </button>
                <button className="button-secondary" disabled={busy.tags || !document.canEdit} onClick={handleTags} type="button">
                  <AppIcon className="h-4 w-4" name="tag" />
                  {busy.tags ? "Generating..." : "Generate tags"}
                </button>
              </div>

              <div className="mt-5 surface-panel">
                <p className="section-label">Summary</p>
                <p className="mt-3 text-sm leading-7 text-slate-700">{document.summary || "No summary available yet."}</p>
              </div>

              <div className="mt-4 grid gap-4 xl:grid-cols-2">
                <div className="surface-panel">
                  <p className="section-label">Suggested Title</p>
                  <p className="mt-3 text-sm font-medium leading-7 text-slate-800">{document.titleSuggestion || "No title suggestion yet."}</p>
                </div>
                <div className="surface-panel">
                  <p className="section-label">Readability</p>
                  <p className="mt-3 text-sm leading-7 text-slate-700">{document.readabilityNote || "No readability note yet."}</p>
                </div>
              </div>

              <div className="mt-4 grid gap-4 xl:grid-cols-2">
                <div className="surface-panel">
                  <p className="section-label">Key Insights</p>
                  <div className="mt-3">{renderSimpleList(document.keyInsights)}</div>
                </div>
                <div className="surface-panel">
                  <p className="section-label">Highlights</p>
                  <div className="mt-3">{renderSimpleList(document.bulletHighlights)}</div>
                </div>
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                {(document.tags || []).length ? (
                  document.tags.map((tag) => (
                    <span key={tag} className="rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700">
                      #{tag}
                    </span>
                  ))
                ) : (
                  <p className="text-sm text-slate-500">No tags yet.</p>
                )}
              </div>

              <div className="mt-4 grid gap-4 xl:grid-cols-3">
                <div className="surface-panel">
                  <p className="section-label">Dates</p>
                  <div className="mt-3">{renderSimpleList(document.smartMetadata?.dates)}</div>
                </div>
                <div className="surface-panel">
                  <p className="section-label">Names</p>
                  <div className="mt-3">{renderSimpleList(document.smartMetadata?.names)}</div>
                </div>
                <div className="surface-panel">
                  <p className="section-label">Keywords</p>
                  <div className="mt-3">{renderSimpleList(document.smartMetadata?.keywords)}</div>
                </div>
              </div>
            </div>

            <div className="rounded-[28px] border border-slate-200 bg-[linear-gradient(180deg,rgba(79,70,229,0.06)_0%,rgba(124,58,237,0.04)_100%)] px-4 py-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Rewrite Mode</p>
                  <p className="mt-2 text-sm font-medium text-slate-900">{selectedRewriteMode.label}</p>
                </div>
                <div className="rounded-full bg-white px-3 py-2 text-xs font-medium text-slate-600 shadow-sm">
                  {selectedRewriteMode.helper}
                </div>
              </div>

              <div className="mt-4 grid gap-2 sm:grid-cols-2">
                {rewriteModeOptions.map((option) => (
                  <button
                    key={option.id}
                    className={`rounded-2xl border px-4 py-3 text-left text-sm transition ${
                      rewriteMode === option.id
                        ? "border-brand-300 bg-white text-brand-700 shadow-sm"
                        : "border-slate-200 bg-transparent text-slate-600 hover:border-brand-200 hover:bg-white"
                    }`}
                    disabled={!document.canEdit || busy.rewritePreview || busy.rewriteSave}
                    onClick={() => setRewriteMode(option.id)}
                    type="button"
                  >
                    <p className="font-medium">{option.label}</p>
                    <p className="mt-1 text-xs opacity-80">{option.helper}</p>
                  </button>
                ))}
              </div>

              <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                <button className="button-secondary" disabled={!document.canEdit || busy.rewritePreview || busy.rewriteSave} onClick={() => handleRewrite(false)} type="button">
                  <AppIcon className="h-4 w-4" name="spark" />
                  {busy.rewritePreview ? "Generating..." : "Preview rewrite"}
                </button>
                <button className="button-primary" disabled={!document.canEdit || busy.rewritePreview || busy.rewriteSave} onClick={() => handleRewrite(true)} type="button">
                  <AppIcon className="h-4 w-4" name="check" />
                  {busy.rewriteSave ? "Saving..." : "Save as version"}
                </button>
              </div>

              <div className="mt-5 rounded-[24px] border border-slate-200 bg-white px-4 py-4 shadow-sm">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Preview</p>
                <div className="mt-3 min-h-48">{renderRewritePreview()}</div>
              </div>

              <div className="mt-5 rounded-[24px] border border-slate-200 bg-white px-4 py-4 shadow-sm">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Related Documents</p>
                {document.relatedDocuments?.length ? (
                  <div className="mt-3 space-y-3">
                    {document.relatedDocuments.map((relatedDocument) => (
                      <Link key={relatedDocument.id} className="block rounded-2xl border border-slate-200 px-4 py-4 transition hover:border-brand-200 hover:bg-slate-50" to={`/files/${relatedDocument.id}`}>
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <p className="truncate text-sm font-medium text-slate-900">{relatedDocument.name}</p>
                            <p className="mt-2 text-sm text-slate-600">{relatedDocument.summary || "Similar document based on current summary, tags, and extracted text."}</p>
                          </div>
                          <span className="label-chip">{formatPercent(relatedDocument.similarityScore)}</span>
                        </div>
                        {relatedDocument.matchedTerms?.length ? (
                          <div className="mt-3 flex flex-wrap gap-2">
                            {relatedDocument.matchedTerms.map((term) => (
                              <span key={`${relatedDocument.id}-${term}`} className="label-chip">{term}</span>
                            ))}
                          </div>
                        ) : null}
                      </Link>
                    ))}
                  </div>
                ) : (
                  <p className="mt-3 text-sm text-slate-500">No similar documents were found yet.</p>
                )}
              </div>
            </div>
          </div>
        </section>

        <div className="space-y-6">
          {renderCurrentVersionPanel()}
          {renderStoragePanel()}
        </div>
      </div>
    );
  }

  function renderCollaborationTab() {
    if (!document.canManageAccess) {
      return (
        <div className="grid gap-6 xl:grid-cols-[1.06fr_0.94fr]">
          <EmptyPanel
            icon="share"
            title="Access controls are limited here"
            description="Only document owners can manage collaborators and public sharing. Your current permission still applies for viewing, reviewing, or editing."
          />
          <section className="card">
            <SectionHeading icon="user" label="Your Access" description="Current workspace access for this document." />
            <div className="mt-6 grid gap-3 sm:grid-cols-2">
              <MetricTile label="Permission" value={document.permission} />
              <MetricTile label="Owner" value={document.ownerName} />
            </div>
          </section>
        </div>
      );
    }

    return (
      <div className="grid gap-6 xl:grid-cols-[1.06fr_0.94fr]">
        <section className="card">
          <SectionHeading
            icon="share"
            label="Collaboration"
            description="Invite collaborators, assign roles, and control public access."
            action={
              <button className="button-secondary w-full sm:w-auto" disabled={busy.toggleLink} onClick={handleToggleShareLink} type="button">
                <AppIcon className="h-4 w-4" name="link" />
                {busy.toggleLink ? "Updating..." : document.publicShareUrl ? "Disable link" : "Enable link"}
              </button>
            }
          />

          <form className="mt-6 grid gap-3 xl:grid-cols-[minmax(0,1fr)_180px_auto]" onSubmit={handleShare}>
            <input
              className="input"
              disabled={busy.share}
              placeholder="Collaborator email"
              value={shareEmail}
              onChange={(event) => setShareEmail(event.target.value)}
            />
            <select className="input" disabled={busy.share} value={sharePermission} onChange={(event) => setSharePermission(event.target.value)}>
              <option value="VIEWER">Viewer</option>
              <option value="EDITOR">Editor</option>
            </select>
            <button className="button-primary" disabled={busy.share} type="submit">
              <AppIcon className="h-4 w-4" name="share" />
              {busy.share ? "Adding..." : "Add collaborator"}
            </button>
          </form>

          <div className="mt-5 surface-panel">
            <p className="section-label">Public Link</p>
            <div className="mt-3 grid gap-3 lg:grid-cols-[180px_minmax(0,1fr)]">
              <select
                className="input"
                disabled={busy.toggleLink || Boolean(document.publicShareUrl)}
                value={shareExpiryDays}
                onChange={(event) => setShareExpiryDays(event.target.value)}
              >
                {shareExpiryOptions.map((option) => (
                  <option key={option.id} value={option.id}>
                    {option.label}
                  </option>
                ))}
              </select>
              <div className="text-sm text-slate-500">
                {document.publicShareUrl ? (
                  document.publicShareExpiresAt
                    ? `Link expires on ${formatDateTime(document.publicShareExpiresAt)}.`
                    : "Link stays active until you disable it."
                ) : document.publicShareExpired ? (
                  document.publicShareExpiresAt
                    ? `The last public link expired on ${formatDateTime(document.publicShareExpiresAt)}. Enable a new link to share again.`
                    : "The previous public link has expired."
                ) : (
                  "Choose how long the public link should stay active before you enable it."
                )}
              </div>
            </div>
            {document.publicShareUrl ? (
              <div className="mt-3 flex flex-col gap-3 lg:flex-row">
                <input className="input flex-1" readOnly value={document.publicShareUrl} />
                <button className="button-secondary" onClick={handleCopyLink} type="button">
                  <AppIcon className="h-4 w-4" name="link" />
                  Copy link
                </button>
              </div>
            ) : (
              <p className="mt-3 text-sm text-slate-500">No public link is active.</p>
            )}
            {copyMessage ? <p className="mt-2 text-xs font-medium text-brand-700">{copyMessage}</p> : null}
          </div>

          <div className="mt-5 space-y-3">
            {document.collaborators?.length ? (
              document.collaborators.map((collaborator) => {
                const rowBusy = busy.collaboratorId === collaborator.id;
                return (
                  <div key={collaborator.id} className="flex flex-col gap-3 rounded-[24px] border border-slate-200 bg-white px-4 py-4 lg:flex-row lg:items-center lg:justify-between">
                    <div className="min-w-0">
                      <p className="font-medium text-slate-950">{collaborator.fullName}</p>
                      <p className="mt-1 text-sm text-slate-500">{collaborator.email}</p>
                    </div>
                    <div className="flex flex-col gap-3 sm:flex-row">
                      <select
                        className="input min-w-36"
                        disabled={rowBusy}
                        value={collaborator.permission}
                        onChange={(event) => handleCollaboratorPermission(collaborator.id, event.target.value)}
                      >
                        <option value="VIEWER">Viewer</option>
                        <option value="EDITOR">Editor</option>
                      </select>
                      <button className="button-secondary" disabled={rowBusy} onClick={() => handleRemoveCollaborator(collaborator.id)} type="button">
                        <AppIcon className="h-4 w-4" name="close" />
                        {rowBusy ? "Updating..." : "Remove"}
                      </button>
                    </div>
                  </div>
                );
              })
            ) : (
              <EmptyPanel icon="share" title="No collaborators yet" description="Invite collaborators to start structured review and controlled document access." />
            )}
          </div>
        </section>

        <div className="space-y-6">
          {renderCurrentVersionPanel()}
          <section className="card">
            <SectionHeading icon="activity" label="Sharing Activity" description="Collaboration changes and public link updates appear in the activity trail." />
            <div className="mt-6">
              <ActivityTimeline items={document.recentActivity} />
            </div>
          </section>
        </div>
      </div>
    );
  }

  function renderActiveTab() {
    if (activeTab === "versions") return renderVersionsTab();
    if (activeTab === "activity") return renderActivityTab();
    if (activeTab === "ai") return renderAiTab();
    if (activeTab === "collaboration") return renderCollaborationTab();
    return renderViewerTab();
  }

  if (loading) {
    return (
      <div>
        <PageHeader title="File Details" subtitle="Loading document details." />
        <div className="grid gap-6 xl:grid-cols-[1.08fr_0.92fr]">
          <div className="card h-72 animate-pulse bg-slate-100" />
          <div className="space-y-6">
            <div className="card h-56 animate-pulse bg-slate-100" />
            <div className="card h-72 animate-pulse bg-slate-100" />
          </div>
        </div>
      </div>
    );
  }

  if (error && !document) {
    return (
      <div>
        <PageHeader title="File Details" subtitle="Document details and actions." />
        <div className="card text-sm text-rose-700">{error}</div>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title={document?.name || "File Details"}
        subtitle="Read the file, manage versions, and update access."
        action={
          <div className="flex flex-col gap-3 sm:flex-row">
            {readyForViewing ? (
              <Link className="button-secondary w-full sm:w-auto" to={`/files/${fileId}/view`}>
                <AppIcon className="h-4 w-4" name="eye" />
                Open viewer
              </Link>
            ) : null}
            <Link className="button-secondary w-full sm:w-auto" to={`/files/${fileId}/versions`}>
              <AppIcon className="h-4 w-4" name="restore" />
              Version history
            </Link>
          </div>
        }
      />

      {error ? (
        <div className="mb-4 rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
          <p>{error}</p>
        </div>
      ) : null}
      {aiStatus ? <p className={`mb-4 rounded-2xl px-4 py-3 text-sm ${statusToneClasses(aiStatus.tone)}`}>{aiStatus.text}</p> : null}
      {!readyForViewing && document?.processingMessage ? (
        <div className={`mb-4 flex flex-col gap-3 rounded-2xl px-4 py-3 text-sm ${document.processingStatus === "FAILED" ? "bg-rose-50 text-rose-700" : "bg-amber-50 text-amber-700"}`}>
          <p>{document.processingMessage}</p>
          {document.processingStatus === "FAILED" && document.canEdit ? (
            <div>
              <button className="button-secondary" disabled={busy.retryProcessing} onClick={handleRetryProcessing} type="button">
                <AppIcon className="h-4 w-4" name="restore" />
                {busy.retryProcessing ? "Retrying..." : "Retry processing"}
              </button>
            </div>
          ) : null}
        </div>
      ) : null}

      <section className="hero-panel">
        <div className="relative z-10 flex flex-col gap-6">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0">
              <div className="flex flex-wrap gap-2">
                <span className="label-chip border-white/10 bg-white/10 text-white">{documentTypeLabel(currentVersion?.mediaType)}</span>
                <span className="label-chip border-white/10 bg-white/10 text-white">{document.permission}</span>
                <span className="label-chip border-white/10 bg-white/10 text-white">
                  {readyForViewing ? `Version ${currentVersion?.versionNumber}` : document.processingStatus === "FAILED" ? "Processing failed" : "Processing"}
                </span>
                {currentVersion?.ocrApplied ? <span className="label-chip border-white/10 bg-white/10 text-white">OCR assisted</span> : null}
              </div>
              <p className="mt-4 text-3xl font-semibold tracking-[-0.05em] text-white sm:text-4xl">{document.name}</p>
              <div className="mt-4 flex flex-wrap items-center gap-3 text-sm text-slate-300">
                <span>{document.ownerName}</span>
                <span className="text-slate-500">
                  <AppIcon className="h-1.5 w-1.5" name="dot" />
                </span>
                <span>{formatDateTime(currentVersion?.createdAt || document.updatedAt)}</span>
                {currentVersion?.origin ? (
                  <>
                    <span className="text-slate-500">
                      <AppIcon className="h-1.5 w-1.5" name="dot" />
                    </span>
                    <span>{formatVersionOrigin(currentVersion.origin)}</span>
                  </>
                ) : null}
              </div>
            </div>

            <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
              <button
                className="button-secondary border-white/10 bg-white/10 text-white hover:bg-white/16"
                disabled={busy.downloadCurrent || !readyForViewing}
                onClick={handleDownloadCurrent}
                type="button"
              >
                <AppIcon className="h-4 w-4" name="download" />
                {busy.downloadCurrent ? "Preparing..." : readyForViewing ? "Download" : "Waiting"}
              </button>
              {document.canManageAccess ? (
                <button className="button-danger" disabled={busy.delete} onClick={handleDelete} type="button">
                  <AppIcon className="h-4 w-4" name="close" />
                  {busy.delete ? "Deleting..." : "Delete"}
                </button>
              ) : null}
            </div>
          </div>

          <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
            <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto_auto]">
              <input
                className="input border-white/10 bg-white/10 text-white placeholder:text-slate-300"
                disabled={!document.canEdit || busy.rename}
                value={renameValue}
                onChange={(event) => setRenameValue(event.target.value)}
              />
              {document.canEdit ? (
                <button className="button-secondary border-white/10 bg-white/10 text-white hover:bg-white/16" disabled={busy.rename} onClick={handleRename} type="button">
                  <AppIcon className="h-4 w-4" name="check" />
                  {busy.rename ? "Saving..." : "Save name"}
                </button>
              ) : null}
              {document.canEdit ? (
                <label className={`button-primary cursor-pointer ${busy.uploadVersion ? "pointer-events-none opacity-70" : ""}`}>
                  <AppIcon className="h-4 w-4" name="upload" />
                  {busy.uploadVersion ? "Uploading..." : "Upload version"}
                  <input aria-label="Upload new version" className="sr-only" accept={SUPPORTED_UPLOAD_ACCEPT} disabled={busy.uploadVersion} onChange={handleVersionUpload} type="file" />
                </label>
              ) : null}
            </div>

            <div className="grid gap-3 sm:grid-cols-3">
              <MetricTile label="Logical" value={formatBytesDetailed(currentVersion?.logicalSize)} tone="hero" />
              <MetricTile label="Actual" value={formatBytesDetailed(currentVersion?.actualStoredSize)} tone="hero" />
              <MetricTile label="Saved" value={formatBytesDetailed(currentVersion?.dedupSavedSize)} tone="hero" />
            </div>
          </div>

          {busy.uploadVersion ? (
            <div>
              <div className="h-2 rounded-full bg-white/10">
                <div className="h-2 rounded-full bg-white transition-all" style={{ width: `${versionUploadProgress}%` }} />
              </div>
              <p className="mt-2 text-sm text-slate-300">{versionUploadProgressLabel(versionUploadProgress)}</p>
            </div>
          ) : (
            document.canEdit ? <p className="text-sm text-slate-300">{UPLOAD_SUPPORT_MESSAGE}</p> : null
          )}
        </div>
      </section>

      <div className="mt-6 card p-3">
        <div className="tab-strip">
          {detailTabs.map((tab) => (
            <button
              key={tab.id}
              className={`tab-pill ${activeTab === tab.id ? "tab-pill-active" : ""}`}
              onClick={() => handleTabChange(tab.id)}
              type="button"
            >
              <AppIcon className="h-4 w-4" name={tab.icon} />
              <span>{tab.label}</span>
            </button>
          ))}
        </div>
        <div className="mt-4 px-2">
          <p className="text-sm text-slate-500">{detailTabs.find((tab) => tab.id === activeTab)?.helper}</p>
        </div>
      </div>

      <div className="mt-6">{renderActiveTab()}</div>
    </div>
  );
}

export default FileDetailsPage;
