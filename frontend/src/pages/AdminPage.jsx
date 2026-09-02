import { useEffect, useMemo, useState } from "react";
import PageHeader from "../components/PageHeader";
import StatCard from "../components/StatCard";
import AppIcon from "../components/ui/AppIcon";
import api from "../services/api";
import { getApiErrorMessage } from "../utils/apiError";

function AdminPage() {
  const [overview, setOverview] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [updatingRoleId, setUpdatingRoleId] = useState(null);

  useEffect(() => {
    loadOverview();
  }, []);

  async function loadOverview() {
    setLoading(true);
    setError("");
    try {
      const { data } = await api.get("/admin/overview");
      setOverview(data.data);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, "Unable to load admin overview"));
    } finally {
      setLoading(false);
    }
  }

  async function handleRoleChange(userId, nextRole) {
    setUpdatingRoleId(userId);
    try {
      await api.patch(`/admin/users/${userId}/role`, { role: nextRole });
      await loadOverview();
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, "Couldn't update user role"));
    } finally {
      setUpdatingRoleId(null);
    }
  }

  const systemHealth = overview?.systemHealth;
  const docs = overview?.documents ?? [];
  const users = overview?.users ?? [];
  const roleOptions = [
    { value: "USER", label: "User" },
    { value: "ADMIN", label: "Admin" },
  ];

  const userSummary = useMemo(
    () => [{
      label: "Total users",
      value: overview?.totalUsers ?? 0,
      accent: "bg-sky-50 text-sky-700",
      helper: "Registered accounts",
      icon: "users",
    }, {
      label: "Admins",
      value: overview?.adminUsers ?? 0,
      accent: "bg-violet-50 text-violet-700",
      helper: "Privileged accounts",
      icon: "shield",
    }, {
      label: "Documents",
      value: overview?.totalDocuments ?? 0,
      accent: "bg-emerald-50 text-emerald-700",
      helper: "Workspace documents",
      icon: "files",
    }, {
      label: "Processing",
      value: overview?.processingDocuments ?? 0,
      accent: "bg-amber-50 text-amber-700",
      helper: "Non-complete uploads",
      icon: "activity",
    }],
    [overview]
  );

  if (loading) {
    return (
      <div>
        <PageHeader title="Admin" subtitle="Loading governance overview." />
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {[1, 2, 3, 4].map((item) => (
            <div key={item} className="card h-28 animate-pulse bg-slate-100" />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <PageHeader title="Admin" subtitle="Manage users, permissions, and workspace health." />
        <div className="card">
          <p className="app-error-banner">{error}</p>
          <button className="button-secondary mt-4" onClick={loadOverview} type="button">
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Admin" subtitle="Manage users, permissions, document oversight, and workspace health." />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {userSummary.map((item) => (
          <StatCard key={item.label} accent={item.accent} helper={item.helper} icon={item.icon} label={item.label} value={item.value} />
        ))}
      </div>

      <section className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
        <div className="card">
          <div className="flex items-center gap-3">
            <span className="app-icon-badge">
              <AppIcon className="h-4.5 w-4.5" name="shield" />
            </span>
            <div>
              <p className="section-label">System health</p>
              <p className="app-text-muted mt-2 text-sm">Current backend, database, and AI availability.</p>
            </div>
          </div>

          <div className="mt-6 space-y-3">
            {[
              { label: "Backend", value: systemHealth?.backendStatus ?? "UNKNOWN" },
              { label: "Database", value: systemHealth?.databaseStatus ?? "UNKNOWN" },
              { label: "AI service", value: systemHealth?.aiServiceStatus ?? "UNKNOWN" },
            ].map((item) => (
              <div key={item.label} className="flex items-center justify-between rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-3">
                <span className="text-sm font-medium text-slate-700">{item.label}</span>
                <span className="rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-semibold uppercase tracking-[0.12em] text-emerald-700">
                  {item.value}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="card">
          <div className="flex items-center gap-3">
            <span className="app-icon-badge">
              <AppIcon className="h-4.5 w-4.5" name="files" />
            </span>
            <div>
              <p className="section-label">Document oversight</p>
              <p className="app-text-muted mt-2 text-sm">Latest document state across the workspace.</p>
            </div>
          </div>

          <div className="mt-6 space-y-3">
            {docs.length === 0 ? (
              <div className="empty-state">
                <p className="app-text-primary mt-4 text-base font-medium">No documents found</p>
              </div>
            ) : (
              docs.slice(0, 6).map((document) => (
                <div key={document.id} className="rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-3">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="font-medium text-slate-800">{document.name}</p>
                      <p className="text-xs text-slate-500">{document.ownerName} · {document.ownerEmail}</p>
                    </div>
                    <span className="inline-flex rounded-full border border-slate-200 bg-white px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-slate-700">
                      {document.processingStatus}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </section>

      <section className="card">
        <div className="flex items-center gap-3">
          <span className="app-icon-badge">
            <AppIcon className="h-4.5 w-4.5" name="users" />
          </span>
          <div>
            <p className="section-label">Users and permissions</p>
            <p className="app-text-muted mt-2 text-sm">Manage access across the workspace.</p>
          </div>
        </div>

        <div className="mt-6 space-y-3">
          {users.map((user) => (
            <div key={user.id} className="flex flex-col gap-3 rounded-[22px] border border-slate-200 bg-slate-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="font-medium text-slate-800">{user.fullName}</p>
                <p className="text-xs text-slate-500">{user.email}</p>
              </div>

              <div className="flex items-center gap-3">
                <span className="inline-flex rounded-full border border-slate-200 bg-white px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-slate-700">
                  {user.documentCount} docs
                </span>

                <div className="inline-flex rounded-full border border-slate-200 bg-white p-1">
                  {roleOptions.map((option) => {
                    const selected = user.role === option.value;
                    return (
                      <button
                        key={option.value}
                        className={`rounded-full px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.12em] transition ${
                          selected
                            ? "bg-slate-900 text-white shadow-sm"
                            : "text-slate-600 hover:bg-slate-100"
                        }`}
                        disabled={updatingRoleId === user.id || selected}
                        onClick={() => handleRoleChange(user.id, option.value)}
                        type="button"
                      >
                        {option.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

export default AdminPage;
