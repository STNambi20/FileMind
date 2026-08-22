import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const fileDetailsPagePath = path.resolve(__dirname, "../src/pages/FileDetailsPage.jsx");
const aiToolsPagePath = path.resolve(__dirname, "../src/pages/AIToolsPage.jsx");
const myFilesPagePath = path.resolve(__dirname, "../src/pages/MyFilesPage.jsx");

test("file details viewer tab uses the shared in-app preview panel", async () => {
  const source = await readFile(fileDetailsPagePath, "utf8");

  assert.equal(source.includes("<DocumentPreviewPanel compact fileId={fileId} viewer={viewer} />"), true);
  assert.equal(source.includes("Viewer Shortcut"), false);
});

test("AI tools open the AI tab instead of generic details", async () => {
  const source = await readFile(aiToolsPagePath, "utf8");

  assert.equal(source.includes("to={`/files/${document.id}?tab=ai`}"), true);
  assert.equal(source.includes("Open AI"), true);
});

test("document list uses Open for the in-app viewer action", async () => {
  const source = await readFile(myFilesPagePath, "utf8");

  assert.equal(source.includes("useDocumentOpenAction"), true);
  assert.equal(source.includes("openActionLabel"), true);
  assert.equal(source.includes("handlePrimaryDocumentAction"), true);
  assert.equal(source.includes("document.searchSnippet"), true);
});
