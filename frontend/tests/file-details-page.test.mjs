import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const fileDetailsPagePath = path.resolve(__dirname, "../src/pages/FileDetailsPage.jsx");

test("file details page renders only the AI rewrite preview section", async () => {
  const source = await readFile(fileDetailsPagePath, "utf8");

  assert.equal(source.includes("Extracted Text Preview"), false);
  assert.equal(source.includes("No text preview available."), false);
  assert.equal(source.includes("No rewrite preview yet."), true);
  assert.equal(source.includes("Review Notes"), true);
  assert.equal(source.includes("Add a short review note"), true);
});

test("file details page exposes the safe document intelligence actions", async () => {
  const source = await readFile(fileDetailsPagePath, "utf8");

  assert.equal(source.includes("Ask the document"), true);
  assert.equal(source.includes("Related Documents"), true);
  assert.equal(source.includes("Retry processing"), true);
  assert.equal(source.includes("The answer is grounded in the current document text."), true);
});
