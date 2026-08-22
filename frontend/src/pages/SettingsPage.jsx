import { useEffect, useState } from "react";
import { useTheme } from "../app/providers/ThemeProvider";
import { usePreferences } from "../app/providers/PreferencesProvider";
import { useToast } from "../app/providers/ToastProvider";
import PageHeader from "../components/PageHeader";
import AppIcon from "../components/ui/AppIcon";
import { getApiErrorMessage } from "../utils/apiError";

const themeOptions = [
  { id: "light", label: "Light", helper: "Bright workspace surfaces", icon: "sun" },
  { id: "dark", label: "Dark", helper: "Lower-glare review mode", icon: "moon" },
  { id: "system", label: "System", helper: "Match your device preference", icon: "desktop" },
];

const summaryLengthOptions = [
  { id: "SHORT", label: "Short", helper: "A quick two-line summary" },
  { id: "DETAILED", label: "Detailed", helper: "A richer overview with more context" },
  { id: "BULLET", label: "Bullet", helper: "Keep bullet highlights front and center" },
];

const rewriteModeOptions = [
  { id: "PROFESSIONAL", label: "Professional" },
  { id: "FORMAL", label: "Formal" },
  { id: "SIMPLE", label: "Simple" },
];

const openModeOptions = [
  { id: "PREVIEW", label: "Preview" },
  { id: "DOWNLOAD", label: "Download" },
];

const uploadBehaviorOptions = [
  { id: "OPEN_FILE", label: "Open file" },
  { id: "STAY_IN_LIST", label: "Stay in list" },
  { id: "GO_TO_DETAILS", label: "Go to details" },
];

const layoutDensityOptions = [
  { id: "COMFORTABLE", label: "Comfortable" },
  { id: "COMPACT", label: "Compact" },
];

const shareVisibilityOptions = [
  { id: "PRIVATE", label: "Private" },
  { id: "SHARED", label: "Shared" },
];

const shareExpiryOptions = [
  { id: "never", label: "No default expiry", value: null },
  { id: "1", label: "1 day", value: 1 },
  { id: "7", label: "7 days", value: 7 },
  { id: "30", label: "30 days", value: 30 },
];

function SettingsSection({ icon, title, description, children, action }) {
  return (
    <section className="card">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex items-start gap-3">
          <span className="app-icon-badge">
            <AppIcon className="h-4.5 w-4.5" name={icon} />
          </span>
          <div>
            <p className="section-label">{title}</p>
            <p className="app-text-muted mt-2 text-sm">{description}</p>
          </div>
        </div>
        {action ? <div className="w-full sm:w-auto">{action}</div> : null}
      </div>
      <div className="mt-6">{children}</div>
    </section>
  );
}

function ToggleRow({ checked, description, id, label, onChange }) {
  return (
    <label className="flex items-start justify-between gap-4 rounded-[24px] border border-slate-200 bg-white px-4 py-4">
      <div className="min-w-0">
        <p className="app-text-primary text-sm font-medium">{label}</p>
        <p className="app-text-muted mt-2 text-sm">{description}</p>
      </div>
      <span className="shrink-0">
        <input checked={checked} className="mt-1 h-4 w-4 accent-brand-600" id={id} onChange={onChange} type="checkbox" />
      </span>
    </label>
  );
}

function SettingsPage() {
  const { themeMode, resolvedTheme, setThemeMode } = useTheme();
  const { account, preferences, ready, loading, saveAccount, savePreferences } = usePreferences();
  const { showToast } = useToast();
  const [preferencesForm, setPreferencesForm] = useState(preferences);
  const [accountForm, setAccountForm] = useState({
    fullName: account?.fullName || "",
    avatarUrl: account?.avatarUrl || "",
  });
  const [savingPreferences, setSavingPreferences] = useState(false);
  const [savingAccount, setSavingAccount] = useState(false);
  const selectedTheme = themeOptions.find((option) => option.id === themeMode) || themeOptions[0];

  useEffect(() => {
    setPreferencesForm(preferences);
  }, [preferences]);

  useEffect(() => {
    setAccountForm({
      fullName: account?.fullName || "",
      avatarUrl: account?.avatarUrl || "",
    });
  }, [account?.avatarUrl, account?.fullName]);

  function updatePreferenceField(field, value) {
    setPreferencesForm((current) => ({ ...current, [field]: value }));
  }

  async function handleSavePreferences() {
    setSavingPreferences(true);
    try {
      await savePreferences(preferencesForm);
      showToast({
        title: "Preferences updated",
        description: "Your workspace behavior changes are now active.",
      });
    } catch (requestError) {
      showToast({
        title: "Settings update failed",
        description: getApiErrorMessage(requestError, "We couldn't save your preferences."),
        tone: "error",
      });
    } finally {
      setSavingPreferences(false);
    }
  }

  async function handleSaveAccount() {
    setSavingAccount(true);
    try {
      await saveAccount(accountForm);
      showToast({
        title: "Account updated",
        description: "Your profile details were saved.",
      });
    } catch (requestError) {
      showToast({
        title: "Account update failed",
        description: getApiErrorMessage(requestError, "We couldn't save your profile details."),
        tone: "error",
      });
    } finally {
      setSavingAccount(false);
    }
  }

  if (!ready && loading) {
    return (
      <div>
        <PageHeader title="Settings" subtitle="Loading your preferences." />
        <div className="grid gap-6 xl:grid-cols-2">
          <div className="card h-72 animate-pulse bg-slate-100" />
          <div className="card h-72 animate-pulse bg-slate-100" />
        </div>
      </div>
    );
  }

  return (
    <div>
      <PageHeader title="Settings" subtitle="Control how FileMind behaves for AI, uploads, viewing, sharing, and accessibility." />

      <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
        <div className="space-y-6">
          <SettingsSection
            icon="settings"
            title="Appearance"
            description="Keep the workspace readable and predictable on this device."
            action={
              <div className="label-chip">
                <AppIcon className="h-4 w-4" name={resolvedTheme === "dark" ? "moon" : "sun"} />
                {selectedTheme.label}
              </div>
            }
          >
            <div className="grid gap-3 md:grid-cols-3">
              {themeOptions.map((option) => (
                <button
                  key={option.id}
                  className={`interactive-card rounded-[1.7rem] border px-5 py-5 text-left transition ${
                    themeMode === option.id
                      ? "app-accent-surface shadow-sm"
                      : "app-surface-elevated app-text-secondary hover:border-brand-200 hover:bg-slate-50"
                  }`}
                  onClick={() => setThemeMode(option.id)}
                  type="button"
                >
                  <span className="app-icon-badge shadow-sm">
                    <AppIcon className="h-4.5 w-4.5" name={option.icon} />
                  </span>
                  <p className="mt-4 font-medium">{option.label}</p>
                  <p className="mt-2 text-sm opacity-80">{option.helper}</p>
                </button>
              ))}
            </div>

            <div className="mt-5 grid gap-4 sm:grid-cols-3">
              <label className="space-y-2">
                <span className="section-label">Layout Density</span>
                <select className="input" value={preferencesForm.layoutDensity} onChange={(event) => updatePreferenceField("layoutDensity", event.target.value)}>
                  {layoutDensityOptions.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <ToggleRow
                checked={preferencesForm.reducedMotion}
                description="Reduce transitions and motion-heavy UI effects."
                id="reduced-motion"
                label="Reduced motion"
                onChange={(event) => updatePreferenceField("reducedMotion", event.target.checked)}
              />
              <ToggleRow
                checked={preferencesForm.highContrastMode}
                description="Increase contrast for text, borders, and focus states."
                id="high-contrast-mode"
                label="High contrast"
                onChange={(event) => updatePreferenceField("highContrastMode", event.target.checked)}
              />
            </div>
          </SettingsSection>

          <SettingsSection
            icon="spark"
            title="AI Preferences"
            description="Set the defaults FileMind should use when you generate summaries, rewrites, and upload-time tags."
          >
            <div className="grid gap-4 lg:grid-cols-3">
              <label className="space-y-2">
                <span className="section-label">Summary Style</span>
                <select className="input" value={preferencesForm.summaryLength} onChange={(event) => updatePreferenceField("summaryLength", event.target.value)}>
                  {summaryLengthOptions.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="space-y-2">
                <span className="section-label">Rewrite Default</span>
                <select className="input" value={preferencesForm.rewriteMode} onChange={(event) => updatePreferenceField("rewriteMode", event.target.value)}>
                  {rewriteModeOptions.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <ToggleRow
                checked={preferencesForm.autoGenerateTagsOnUpload}
                description="Add AI-generated tags when a new upload finishes processing."
                id="auto-generate-tags-on-upload"
                label="Auto-generate tags"
                onChange={(event) => updatePreferenceField("autoGenerateTagsOnUpload", event.target.checked)}
              />
            </div>
          </SettingsSection>

          <SettingsSection icon="eye" title="Viewer & Document Behavior" description="Choose how FileMind opens and renders documents by default.">
            <div className="grid gap-4 lg:grid-cols-3">
              <label className="space-y-2">
                <span className="section-label">Default Open Mode</span>
                <select className="input" value={preferencesForm.defaultOpenMode} onChange={(event) => updatePreferenceField("defaultOpenMode", event.target.value)}>
                  {openModeOptions.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <ToggleRow
                checked={preferencesForm.openLatestVersionAutomatically}
                description="When no version is selected explicitly, open the newest completed revision in the viewer."
                id="open-latest-version-automatically"
                label="Open latest version"
                onChange={(event) => updatePreferenceField("openLatestVersionAutomatically", event.target.checked)}
              />
              <ToggleRow
                checked={preferencesForm.wrapTextForTextFiles}
                description="Wrap long lines for text-based files inside the built-in preview."
                id="wrap-text-for-text-files"
                label="Wrap text"
                onChange={(event) => updatePreferenceField("wrapTextForTextFiles", event.target.checked)}
              />
            </div>
          </SettingsSection>

          <SettingsSection icon="upload" title="Upload & Processing" description="Control how FileMind behaves after upload and how it treats failed background processing.">
            <div className="grid gap-4 lg:grid-cols-2">
              <label className="space-y-2">
                <span className="section-label">After Upload</span>
                <select className="input" value={preferencesForm.afterUploadBehavior} onChange={(event) => updatePreferenceField("afterUploadBehavior", event.target.value)}>
                  {uploadBehaviorOptions.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <div className="space-y-3">
                <ToggleRow
                  checked={preferencesForm.autoRetryFailedProcessing}
                  description="Allow FileMind to attempt one safe automatic retry when processing fails."
                  id="auto-retry-failed-processing"
                  label="Auto-retry failed processing"
                  onChange={(event) => updatePreferenceField("autoRetryFailedProcessing", event.target.checked)}
                />
                <ToggleRow
                  checked={preferencesForm.keepFailedUploadsVisible}
                  description="Keep failed uploads in the documents list so they can be reviewed and retried."
                  id="keep-failed-uploads-visible"
                  label="Keep failed uploads visible"
                  onChange={(event) => updatePreferenceField("keepFailedUploadsVisible", event.target.checked)}
                />
              </div>
            </div>
          </SettingsSection>
        </div>

        <div className="space-y-6">
          <SettingsSection
            icon="user"
            title="Account"
            description="Update the profile details shown in the top bar and activity views."
            action={
              <button className="button-primary w-full sm:w-auto" disabled={savingAccount} onClick={handleSaveAccount} type="button">
                <AppIcon className="h-4 w-4" name="check" />
                {savingAccount ? "Saving..." : "Save account"}
              </button>
            }
          >
            <div className="grid gap-4">
              <label className="space-y-2">
                <span className="section-label">Name</span>
                <input className="input" value={accountForm.fullName} onChange={(event) => setAccountForm((current) => ({ ...current, fullName: event.target.value }))} />
              </label>
              <label className="space-y-2">
                <span className="section-label">Email</span>
                <input className="input" readOnly value={account?.email || ""} />
              </label>
              <label className="space-y-2">
                <span className="section-label">Avatar URL</span>
                <input
                  className="input"
                  placeholder="https://example.com/avatar.png"
                  value={accountForm.avatarUrl}
                  onChange={(event) => setAccountForm((current) => ({ ...current, avatarUrl: event.target.value }))}
                />
              </label>
            </div>
          </SettingsSection>

          <SettingsSection icon="share" title="Access & Sharing Defaults" description="Control how new uploads should be shared when you enable public access by default.">
            <div className="grid gap-4 lg:grid-cols-2">
              <label className="space-y-2">
                <span className="section-label">Default Visibility</span>
                <select className="input" value={preferencesForm.defaultVisibility} onChange={(event) => updatePreferenceField("defaultVisibility", event.target.value)}>
                  {shareVisibilityOptions.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="space-y-2">
                <span className="section-label">Default Share Expiry</span>
                <select
                  className="input"
                  value={preferencesForm.defaultShareExpiryDays == null ? "never" : String(preferencesForm.defaultShareExpiryDays)}
                  onChange={(event) =>
                    updatePreferenceField("defaultShareExpiryDays", event.target.value === "never" ? null : Number(event.target.value))
                  }
                >
                  {shareExpiryOptions.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <p className="app-text-muted mt-4 text-sm">
              If you choose <span className="font-medium">Shared</span>, new uploads will create a public link after processing completes using this default expiry.
            </p>
          </SettingsSection>

          <SettingsSection icon="activity" title="Notifications" description="Decide which workspace events should raise in-app notifications.">
            <div className="space-y-3">
              <ToggleRow
                checked={preferencesForm.notifyUploadCompleted}
                description="Show a toast when a new upload finishes processing successfully."
                id="notify-upload-completed"
                label="Upload completed"
                onChange={(event) => updatePreferenceField("notifyUploadCompleted", event.target.checked)}
              />
              <ToggleRow
                checked={preferencesForm.notifyProcessingFailed}
                description="Show a toast when processing fails for an upload or version."
                id="notify-processing-failed"
                label="Processing failed"
                onChange={(event) => updatePreferenceField("notifyProcessingFailed", event.target.checked)}
              />
              <ToggleRow
                checked={preferencesForm.notifyVersionCreated}
                description="Show a toast when a new version is created from upload, restore, or AI rewrite."
                id="notify-version-created"
                label="Version created"
                onChange={(event) => updatePreferenceField("notifyVersionCreated", event.target.checked)}
              />
            </div>
          </SettingsSection>

          <SettingsSection
            icon="check"
            title="Save Behavior"
            description="Apply preference changes immediately across uploads, AI actions, viewing, and notifications."
            action={
              <button className="button-primary w-full sm:w-auto" disabled={savingPreferences} onClick={handleSavePreferences} type="button">
                <AppIcon className="h-4 w-4" name="check" />
                {savingPreferences ? "Saving..." : "Save preferences"}
              </button>
            }
          >
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="app-surface-elevated rounded-[24px] border px-4 py-4">
                <p className="section-label">Current Theme</p>
                <p className="app-text-primary mt-3 text-lg font-semibold">{resolvedTheme === "dark" ? "Dark mode" : "Light mode"}</p>
              </div>
              <div className="app-surface-elevated rounded-[24px] border px-4 py-4">
                <p className="section-label">Saved For</p>
                <p className="app-text-primary mt-3 text-lg font-semibold">{account?.fullName || "Current user"}</p>
              </div>
            </div>
            <p className="app-text-muted mt-4 text-sm">
              Preference updates are stored per account and take effect without changing FileMind&apos;s core storage, versioning, or AI pipelines.
            </p>
          </SettingsSection>
        </div>
      </div>
    </div>
  );
}

export default SettingsPage;
