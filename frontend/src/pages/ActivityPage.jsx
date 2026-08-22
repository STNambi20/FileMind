import { useEffect, useMemo, useState } from "react";
import ActivityTimeline from "../components/dashboard/ActivityTimeline";
import PageHeader from "../components/PageHeader";
import StatCard from "../components/StatCard";
import AppIcon from "../components/ui/AppIcon";
import { getRecentActivity } from "../services/dashboardService";
import { getApiErrorMessage } from "../utils/apiError";
import { subscribeWorkspaceUpdates } from "../utils/workspaceEvents";

function ActivityPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function loadActivity() {
    setLoading(true);
    setError("");
    getRecentActivity()
      .then(setItems)
      .catch((requestError) => setError(getApiErrorMessage(requestError, "Unable to load activity")))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadActivity();
    const unsubscribe = subscribeWorkspaceUpdates(() => loadActivity());
    return unsubscribe;
  }, []);

  const metrics = useMemo(() => {
    const reviewEvents = items.filter((item) => item.action === "Document viewed" || item.action === "Document downloaded").length;
    const versions = items.filter((item) => item.action.includes("Version")).length;
    const aiActions = items.filter((item) => item.action.includes("Summary") || item.action.includes("Tags") || item.action.includes("Rewrite")).length;
    const accessChanges = items.filter((item) => item.action.includes("Access") || item.action.includes("link") || item.action.includes("Link")).length;
    return { reviewEvents, versions, aiActions, accessChanges };
  }, [items]);

  return (
    <div>
      <PageHeader title="Activity" subtitle="Workspace events, access changes, and recent review flow." />

      {error ? <p className="app-error-banner mb-4">{error}</p> : null}

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard accent="bg-cyan-50 text-cyan-700" helper="Document opens and download actions" icon="eye" label="Review Events" value={metrics.reviewEvents} />
        <StatCard accent="bg-brand-50 text-brand-700" helper="Uploads, restores, and version saves" icon="restore" label="Version Events" value={metrics.versions} />
        <StatCard accent="bg-violet-50 text-violet-700" helper="Summaries, tags, and rewrites" icon="spark" label="AI Actions" value={metrics.aiActions} />
        <StatCard accent="bg-amber-50 text-amber-700" helper="Collaborator and public-link changes" icon="share" label="Access Changes" value={metrics.accessChanges} />
      </div>

      <div className="mt-6 card">
        <div className="flex items-center gap-3">
          <span className="app-icon-badge">
            <AppIcon className="h-4.5 w-4.5" name="activity" />
          </span>
          <div>
            <p className="section-label">Timeline</p>
            <p className="app-text-muted mt-2 text-sm">A real audit trail for uploads, views, version changes, collaboration, and AI actions.</p>
          </div>
        </div>

        <div className="mt-6">
          {loading ? <p className="app-text-muted text-sm">Loading activity...</p> : <ActivityTimeline items={items} />}
        </div>
      </div>
    </div>
  );
}

export default ActivityPage;
