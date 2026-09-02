import { useEffect, useMemo, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useToast } from "../app/providers/ToastProvider";
import { useAuth } from "../app/providers/AuthProvider";
import { useTheme } from "../app/providers/ThemeProvider";
import AppFooter from "../components/layout/AppFooter";
import BrandMark from "../components/layout/BrandMark";
import AppIcon from "../components/ui/AppIcon";

const baseNavItems = [
  { label: "Dashboard", to: "/dashboard", icon: "dashboard", helper: "Workspace metrics" },
  { label: "Documents", to: "/files", icon: "files", helper: "Storage and review" },
  { label: "Activity", to: "/activity", icon: "activity", helper: "Audit and access" },
  { label: "AI Tools", to: "/ai-tools", icon: "spark", helper: "Document intelligence" },
  { label: "Settings", to: "/settings", icon: "settings", helper: "Theme and preferences" },
];

function userInitials(value) {
  if (!value) return "FM";
  return value
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || "")
    .join("") || "FM";
}

function roleLabel(value) {
  if (!value) return "Workspace user";
  return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();
}

function renderUserAvatar(user, className) {
  if (user?.avatarUrl) {
    return <img alt={user.fullName || "Profile"} className={className} src={user.avatarUrl} />;
  }
  return userInitials(user?.fullName);
}

function AppLayout() {
  const location = useLocation();
  const { user, logout } = useAuth();
  const { themeMode, resolvedTheme, setThemeMode } = useTheme();
  const { showToast } = useToast();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  useEffect(() => {
    setMobileNavOpen(false);
  }, [location.pathname]);

  const navItems = useMemo(
    () => (user?.role === "ADMIN" ? [...baseNavItems, { label: "Admin", to: "/admin", icon: "shield", helper: "Users and governance" }] : baseNavItems),
    [user?.role],
  );

  const currentNav = useMemo(
    () => navItems.find((item) => location.pathname === item.to || location.pathname.startsWith(`${item.to}/`)) || navItems[0],
    [location.pathname, navItems],
  );

  function handleLogout() {
    logout();
    showToast({
      title: "Signed out",
      description: "You have been signed out on this device.",
    });
  }

  function toggleTheme() {
    if (themeMode === "system") {
      setThemeMode(resolvedTheme === "dark" ? "light" : "dark");
      return;
    }
    setThemeMode(resolvedTheme === "dark" ? "light" : "dark");
  }

  function renderNavigation(isMobile = false) {
    return (
      <nav className="mt-8 space-y-2">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `group flex items-center gap-3 rounded-[22px] border px-4 py-3 transition duration-150 ${
                isActive
                  ? "border-[var(--app-border-strong)] bg-[var(--app-accent-surface)] text-[var(--app-text)] shadow-[0_18px_38px_rgba(15,23,42,0.08)]"
                  : isMobile
                    ? "border-transparent text-[var(--app-text-muted)] hover:border-[var(--app-border)] hover:bg-[var(--app-surface-subtle)] hover:text-[var(--app-text)]"
                    : "border-transparent text-[var(--app-text-muted)] hover:border-[var(--app-border)] hover:bg-[var(--app-surface-subtle)] hover:text-[var(--app-text)]"
              }`
            }
          >
            <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[var(--app-surface-subtle)] text-inherit transition group-hover:bg-[var(--app-surface-elevated)]">
              <AppIcon className="h-4.5 w-4.5" name={item.icon} />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-sm font-medium">{item.label}</span>
              <span className="mt-1 block text-xs text-[var(--app-text-muted)]">{item.helper}</span>
            </span>
          </NavLink>
        ))}
      </nav>
    );
  }

  return (
    <div className="min-h-screen bg-transparent">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      {mobileNavOpen ? <div className="fixed inset-0 z-40 bg-slate-950/60 backdrop-blur-sm lg:hidden" onClick={() => setMobileNavOpen(false)} /> : null}

      <div className="mx-auto flex min-h-screen w-full max-w-[1680px]">
        <aside className="hidden w-[19.5rem] shrink-0 border-r border-[var(--app-border)] bg-[var(--app-surface)] px-6 py-7 text-[var(--app-text)] lg:flex">
          <div className="flex h-full w-full flex-col">
            <div className="rounded-[30px] border border-[var(--app-border)] bg-[var(--app-surface-subtle)] p-5 backdrop-blur-sm">
              <BrandMark subtitle="Intelligent document platform" theme={resolvedTheme === "dark" ? "dark" : "light"} />
            </div>

            <div className="mt-6 rounded-[30px] border border-[var(--app-border)] bg-[var(--app-surface-subtle)] p-4">
              <p className="section-label">Navigation</p>
              {renderNavigation()}
            </div>

            <div className="mt-auto space-y-4">
              <div className="rounded-[28px] border border-[var(--app-border)] bg-[var(--app-surface-subtle)] p-4">
                <p className="section-label">Workspace</p>
                <p className="mt-3 text-sm font-medium text-[var(--app-text)]">Storage-aware review and AI-assisted version control.</p>
                <div className="mt-4 flex items-center gap-2">
                  <span className="inline-flex items-center gap-2 rounded-full border border-[var(--app-border)] bg-[var(--app-surface-elevated)] px-3 py-1.5 text-xs text-[var(--app-text-secondary)]">
                    <span className="h-2 w-2 rounded-full bg-emerald-400" />
                    Live workspace
                  </span>
                  <span className="inline-flex items-center gap-2 rounded-full border border-[var(--app-border)] bg-[var(--app-surface-elevated)] px-3 py-1.5 text-xs text-[var(--app-text-secondary)]">
                    {resolvedTheme === "dark" ? "Dark theme" : "Light theme"}
                  </span>
                </div>
              </div>

              <div className="rounded-[28px] border border-[var(--app-border)] bg-[var(--app-surface-subtle)] p-4">
                <p className="text-xs uppercase tracking-[0.18em] text-[var(--app-text-muted)]">Session</p>
                <div className="mt-4 flex items-center gap-3">
                  <span className="flex h-12 w-12 items-center justify-center rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface-elevated)] text-sm font-semibold tracking-[0.08em] text-[var(--app-text)] shadow-[0_10px_24px_rgba(15,23,42,0.08)]">
                    {renderUserAvatar(user, "h-full w-full rounded-2xl object-cover")}
                  </span>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-[var(--app-text)]">{user?.fullName}</p>
                    <p className="mt-1 truncate text-xs text-[var(--app-text-muted)]">{user?.email}</p>
                  </div>
                </div>
                <div className="mt-4 flex items-center justify-between rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface-elevated)] px-3 py-2 text-xs text-[var(--app-text-secondary)]">
                  <span>{roleLabel(user?.role)}</span>
                  <span className="rounded-full border border-emerald-400/18 bg-emerald-400/12 px-2 py-1 font-medium text-emerald-600 dark:text-emerald-200">Active</span>
                </div>
              </div>
            </div>
          </div>
        </aside>

        <aside
          aria-hidden={!mobileNavOpen}
          className={`fixed inset-y-0 left-0 z-50 w-80 max-w-[calc(100vw-1.5rem)] transform border-r border-[var(--app-border)] bg-[var(--app-surface)] px-5 py-6 text-[var(--app-text)] shadow-2xl transition-transform duration-200 lg:hidden ${
            mobileNavOpen ? "translate-x-0" : "-translate-x-full"
          }`}
          id="mobile-navigation"
          inert={mobileNavOpen ? undefined : ""}
        >
          <div className="flex items-start justify-between gap-4">
            <BrandMark subtitle="Intelligent document platform" theme={resolvedTheme === "dark" ? "dark" : "light"} />
            <button aria-label="Close navigation" className="button-ghost text-[var(--app-text)] hover:bg-[var(--app-surface-subtle)]" onClick={() => setMobileNavOpen(false)} type="button">
              <AppIcon className="h-4 w-4" name="close" />
            </button>
          </div>

          <div className="mt-6 rounded-[28px] border border-[var(--app-border)] bg-[var(--app-surface-subtle)] p-4">
            <p className="section-label">Navigation</p>
            {renderNavigation(true)}
          </div>
        </aside>

        <main className="flex min-h-screen min-w-0 flex-1 flex-col" id="main-content">
          <header className="relative z-20 px-4 py-4 sm:px-6 lg:px-8">
            <div className="shell-glass mx-auto flex w-full max-w-7xl flex-col gap-4 rounded-[28px] px-4 py-4 shadow-[0_12px_28px_rgba(15,23,42,0.06)] sm:px-6 lg:flex-row lg:items-center lg:justify-between">
              <div className="flex items-start gap-3 sm:items-center">
                <button aria-controls="mobile-navigation" aria-expanded={mobileNavOpen} className="button-ghost shrink-0 lg:hidden" onClick={() => setMobileNavOpen(true)} type="button">
                  <AppIcon className="h-4 w-4" name="menu" />
                  <span>Menu</span>
                </button>
                <div className="min-w-0">
                  <p className="section-label">Current Workspace</p>
                  <div className="mt-2 flex flex-wrap items-center gap-3">
                    <p className="app-text-primary text-base font-semibold">{currentNav.label}</p>
                    <span className="label-chip">{currentNav.helper}</span>
                  </div>
                </div>
              </div>

              <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-end">
                <button className="button-secondary" onClick={toggleTheme} type="button">
                  <AppIcon className="h-4 w-4" name={resolvedTheme === "dark" ? "sun" : "moon"} />
                  {themeMode === "system" ? `System (${resolvedTheme})` : resolvedTheme === "dark" ? "Dark mode" : "Light mode"}
                </button>
                <div className="user-summary-card">
                  <span className="user-summary-avatar">
                    {renderUserAvatar(user, "h-full w-full rounded-2xl object-cover")}
                  </span>
                  <div className="min-w-0">
                    <p className="user-summary-name">{user?.fullName}</p>
                    <p className="user-summary-email">{user?.email}</p>
                  </div>
                </div>
                <button className="button-secondary" onClick={handleLogout} type="button">
                  <AppIcon className="h-4 w-4" name="logout" />
                  Logout
                </button>
              </div>
            </div>
          </header>

          <div className="mx-auto flex w-full max-w-7xl flex-1 flex-col px-4 pb-6 sm:px-6 lg:px-8">
            <div key={location.pathname} className="page-transition flex-1 py-2 sm:py-4">
              <Outlet />
            </div>
          </div>

          <AppFooter />
        </main>
      </div>
    </div>
  );
}

export default AppLayout;
