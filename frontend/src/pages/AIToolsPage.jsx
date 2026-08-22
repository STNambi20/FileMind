import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import PageHeader from "../components/PageHeader";
import AppIcon from "../components/ui/AppIcon";
import StatCard from "../components/StatCard";
import { listDocuments } from "../services/documentService";
import { getApiErrorMessage } from "../utils/apiError";
import { formatCount } from "../utils/formatters";
import { subscribeWorkspaceUpdates } from "../utils/workspaceEvents";

function AIToolsPage() {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function loadDocuments() {
    setLoading(true);
    setError("");
    listDocuments()
      .then(setDocuments)
      .catch((requestError) => setError(getApiErrorMessage(requestError, "Unable to load AI-ready documents")))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadDocuments();
    const unsubscribe = subscribeWorkspaceUpdates(() => loadDocuments());
    return unsubscribe;
  }, []);

  const documentsWithSummary = useMemo(() => documents.filter((document) => Boolean(document.summary)).length, [documents]);
  const documentsWithTags = useMemo(() => documents.filter((document) => (document.tags || []).length > 0).length, [documents]);
  const documentsWithAiOutputs = useMemo(
    () => documents.filter((document) => document.summary || (document.tags || []).length > 0).length,
    [documents],
  );
  const orderedDocuments = useMemo(() => {
    return [...documents].sort((left, right) => {
      const leftScore = Number(Boolean(left.summary)) + Number((left.tags || []).length > 0);
      const rightScore = Number(Boolean(right.summary)) + Number((right.tags || []).length > 0);
      return rightScore - leftScore;
    });
  }, [documents]);

  return (
    <div>
      <PageHeader title="AI Tools" subtitle="Open a document to generate summaries, tags, or rewrites." />

      <div className="grid gap-4 md:grid-cols-3">
        <StatCard accent="bg-brand-50 text-brand-700" helper="Documents available for AI actions" icon="spark" label="Documents" value={formatCount(documents.length)} />
        <StatCard accent="bg-emerald-50 text-emerald-700" helper="Latest versions with saved summaries" icon="summary" label="Summaries" value={formatCount(documentsWithSummary)} />
        <StatCard accent="bg-sky-50 text-sky-700" helper="Latest versions with saved tags" icon="tag" label="Tagged" value={formatCount(documentsWithTags)} />
      </div>

      <div className="mt-6 card">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="section-label">Documents</p>
            <p className="mt-2 text-sm text-slate-500">
              {formatCount(documentsWithAiOutputs)} documents already include saved AI output.
            </p>
          </div>
          <Link className="button-secondary w-full sm:w-auto" to="/files">
            <AppIcon className="h-4 w-4" name="files" />
            Open documents
          </Link>
        </div>

        <div className="mt-6">
          {loading ? (
            <p className="text-sm text-slate-500">Loading documents...</p>
          ) : error ? (
            <div>
              <p className="text-sm text-rose-700">{error}</p>
              <button className="button-secondary mt-4" onClick={loadDocuments} type="button">
                Retry
              </button>
            </div>
          ) : documents.length === 0 ? (
            <div className="rounded-3xl bg-slate-50 px-4 py-5 text-sm text-slate-500">Upload a document to start using AI tools.</div>
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              {orderedDocuments.map((document) => (
                <div key={document.id} className="rounded-[1.7rem] border border-slate-200 bg-white px-4 py-4 shadow-sm">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <div className="flex items-center gap-3">
                        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                          <AppIcon className="h-4.5 w-4.5" name="spark" />
                        </span>
                        <div className="min-w-0">
                          <p className="truncate font-medium text-slate-950">{document.name}</p>
                          <div className="mt-2 flex flex-wrap gap-2">
                            <span className="label-chip">{document.summary ? "Summary saved" : "No summary"}</span>
                            <span className="label-chip">{(document.tags || []).length ? `${formatCount(document.tags.length)} tags` : "No tags"}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                    <Link className="button-primary shrink-0" to={`/files/${document.id}?tab=ai`}>
                      Open AI
                    </Link>
                  </div>

                  {document.summary ? (
                    <div className="mt-5 rounded-3xl bg-slate-50 px-4 py-4">
                      <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Latest Summary</p>
                      <p className="mt-3 text-sm leading-7 text-slate-700">{document.summary}</p>
                    </div>
                  ) : null}

                  <div className="mt-4 flex flex-wrap gap-2">
                    {(document.tags || []).length ? (
                      document.tags.map((tag) => (
                        <span key={tag} className="rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700">
                          #{tag}
                        </span>
                      ))
                    ) : (
                      <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">No tags yet</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default AIToolsPage;
