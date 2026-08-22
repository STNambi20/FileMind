import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import AppFooter from "../components/layout/AppFooter";
import BrandMark from "../components/layout/BrandMark";
import PageHeader from "../components/PageHeader";
import AppIcon from "../components/ui/AppIcon";
import { downloadSharedDocumentUrl, getSharedDocument } from "../services/documentService";
import { getApiErrorMessage } from "../utils/apiError";
import { formatBytes, formatDateTime } from "../utils/formatters";
import { documentTypeLabel } from "../utils/productPresentation";

function SharedFilePage() {
  const { token } = useParams();
  const [document, setDocument] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    setLoading(true);
    setError("");
    getSharedDocument(token)
      .then(setDocument)
      .catch((requestError) => setError(getApiErrorMessage(requestError, "Unable to open the shared file.")))
      .finally(() => setLoading(false));
  }, [token]);

  if (loading) {
    return (
      <div className="flex min-h-screen flex-col">
        <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 sm:px-6 lg:px-8">
          <BrandMark subtitle="Shared access" theme="light" />
          <div className="mt-8">
            <PageHeader title="Shared Document" subtitle="Loading read-only access." />
            <div className="card text-sm text-slate-500">Preparing the shared file...</div>
          </div>
        </div>
        <AppFooter />
      </div>
    );
  }

  if (error || !document) {
    return (
      <div className="flex min-h-screen flex-col">
        <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 sm:px-6 lg:px-8">
          <BrandMark subtitle="Shared access" theme="light" />
          <div className="mt-8">
            <PageHeader title="Shared Document" subtitle="Read-only access." />
            <div className="card text-sm text-rose-700">{error || "This shared file is unavailable."}</div>
          </div>
        </div>
        <AppFooter />
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col">
      <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 sm:px-6 lg:px-8">
        <BrandMark subtitle="Shared access" theme="light" />

        <div className="mt-8">
          <PageHeader
            title={document.name}
            subtitle={`Shared by ${document.ownerName}`}
            action={
              <a className="button-primary w-full sm:w-auto" href={downloadSharedDocumentUrl(token)} rel="noreferrer" target="_blank">
                <AppIcon className="h-4 w-4" name="download" />
                Download latest version
              </a>
            }
          />
        </div>

        <div className="grid gap-6 lg:grid-cols-[1.04fr_0.96fr]">
          <div className="card">
            <div className="flex items-center gap-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                <AppIcon className="h-4.5 w-4.5" name="files" />
              </span>
              <div>
                <p className="section-label">Document Snapshot</p>
                <p className="mt-2 text-sm text-slate-500">Latest shared version.</p>
              </div>
            </div>

            <div className="mt-6 grid gap-4 sm:grid-cols-2">
              <div className="rounded-3xl bg-slate-50 p-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Type</p>
                <p className="mt-2 text-sm font-medium text-slate-900">{documentTypeLabel(document.mediaType)}</p>
              </div>
              <div className="rounded-3xl bg-slate-50 p-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Version</p>
                <p className="mt-2 text-sm font-medium text-slate-900">Version {document.currentVersionNumber}</p>
              </div>
              <div className="rounded-3xl bg-slate-50 p-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Logical Size</p>
                <p className="mt-2 text-sm font-medium text-slate-900">{formatBytes(document.logicalSize)}</p>
              </div>
              <div className="rounded-3xl bg-slate-50 p-4">
                <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Updated</p>
                <p className="mt-2 text-sm font-medium text-slate-900">{formatDateTime(document.updatedAt)}</p>
              </div>
            </div>

            <div className="mt-6 rounded-3xl bg-slate-50 px-4 py-4">
              <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Summary</p>
              <p className="mt-3 text-sm leading-7 text-slate-700">{document.summary || "No summary available yet."}</p>
            </div>
          </div>

          <div className="space-y-6">
            <div className="card">
              <div className="flex items-center gap-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand-50 text-brand-700">
                  <AppIcon className="h-4.5 w-4.5" name="tag" />
                </span>
                <div>
                  <p className="section-label">Tags</p>
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
            </div>

            <div className="card">
              <div className="flex items-center gap-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                  <AppIcon className="h-4.5 w-4.5" name="summary" />
                </span>
                <div>
                  <p className="section-label">Text Preview</p>
                </div>
              </div>
              <p className="mt-4 whitespace-pre-wrap text-sm leading-7 text-slate-600">{document.extractedTextPreview || "No preview available."}</p>
            </div>
          </div>
        </div>
      </div>
      <AppFooter />
    </div>
  );
}

export default SharedFilePage;
