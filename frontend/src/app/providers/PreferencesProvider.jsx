import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { useAuth } from "./AuthProvider";
import { getSettings as fetchSettings, updateAccount as updateAccountRequest, updatePreferences as updatePreferencesRequest } from "../../services/settingsService";

const PreferencesContext = createContext(null);

export const DEFAULT_PREFERENCES = {
  summaryLength: "DETAILED",
  rewriteMode: "PROFESSIONAL",
  autoGenerateTagsOnUpload: true,
  openLatestVersionAutomatically: false,
  wrapTextForTextFiles: true,
  defaultOpenMode: "PREVIEW",
  autoRetryFailedProcessing: false,
  keepFailedUploadsVisible: false,
  afterUploadBehavior: "STAY_IN_LIST",
  notifyUploadCompleted: true,
  notifyProcessingFailed: true,
  notifyVersionCreated: true,
  defaultShareExpiryDays: null,
  defaultVisibility: "PRIVATE",
  layoutDensity: "COMFORTABLE",
  reducedMotion: false,
  highContrastMode: false,
};

function applyAccessibilityPreferences(preferences) {
  if (typeof document === "undefined") {
    return;
  }

  document.documentElement.dataset.density = (preferences.layoutDensity || "COMFORTABLE").toLowerCase();
  document.documentElement.dataset.reducedMotion = String(Boolean(preferences.reducedMotion));
  document.documentElement.dataset.highContrast = String(Boolean(preferences.highContrastMode));
}

export function PreferencesProvider({ children }) {
  const { isAuthenticated, user, updateCurrentUser } = useAuth();
  const [settings, setSettings] = useState(null);
  const [loading, setLoading] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      setSettings(null);
      setLoading(false);
      setReady(true);
      applyAccessibilityPreferences(DEFAULT_PREFERENCES);
      return;
    }

    setLoading(true);
    fetchSettings()
      .then((response) => {
        setSettings(response);
        updateCurrentUser(response.account);
      })
      .catch(() => {
        setSettings({
          account: user,
          preferences: DEFAULT_PREFERENCES,
        });
      })
      .finally(() => {
        setLoading(false);
        setReady(true);
      });
  }, [isAuthenticated, user?.id]);

  const preferences = settings?.preferences || DEFAULT_PREFERENCES;
  const account = settings?.account || user || null;

  useEffect(() => {
    applyAccessibilityPreferences(preferences);
  }, [preferences]);

  async function savePreferences(payload) {
    const response = await updatePreferencesRequest(payload);
    setSettings(response);
    updateCurrentUser(response.account);
    return response;
  }

  async function saveAccount(payload) {
    const response = await updateAccountRequest(payload);
    setSettings(response);
    updateCurrentUser(response.account);
    return response;
  }

  const value = useMemo(
    () => ({
      account,
      preferences,
      settings,
      loading,
      ready,
      savePreferences,
      saveAccount,
    }),
    [account, loading, preferences, ready, settings, saveAccount, savePreferences],
  );

  return <PreferencesContext.Provider value={value}>{children}</PreferencesContext.Provider>;
}

export function usePreferences() {
  const context = useContext(PreferencesContext);
  if (!context) {
    throw new Error("usePreferences must be used within PreferencesProvider");
  }
  return context;
}
