import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const settingsPagePath = path.resolve(__dirname, "../src/pages/SettingsPage.jsx");
const preferencesProviderPath = path.resolve(__dirname, "../src/app/providers/PreferencesProvider.jsx");

test("settings page is wired to persisted account and preference saving", async () => {
  const [settingsSource, providerSource] = await Promise.all([
    readFile(settingsPagePath, "utf8"),
    readFile(preferencesProviderPath, "utf8"),
  ]);

  assert.equal(settingsSource.includes("savePreferences"), true);
  assert.equal(settingsSource.includes("saveAccount"), true);
  assert.equal(settingsSource.includes("Auto-retry failed processing"), true);
  assert.equal(settingsSource.includes("Default Share Expiry"), true);
  assert.equal(providerSource.includes('api.get("/settings")') || providerSource.includes("fetchSettings"), true);
  assert.equal(providerSource.includes("document.documentElement.dataset.density"), true);
});
