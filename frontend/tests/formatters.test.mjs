import test from "node:test";
import assert from "node:assert/strict";

import { formatBytesDetailed, formatPercent } from "../src/utils/formatters.js";

test("formatBytesDetailed includes exact bytes alongside human-readable units", () => {
  assert.equal(formatBytesDetailed(58_963_212), "56 MB (58,963,212 bytes)");
  assert.equal(formatBytesDetailed(17_440_128), "17 MB (17,440,128 bytes)");
  assert.equal(formatBytesDetailed(0), "0 B (0 bytes)");
});

test("formatPercent keeps storage ratios readable without hiding zero", () => {
  assert.equal(formatPercent(0), "0%");
  assert.equal(formatPercent(0.096), "9.6%");
  assert.equal(formatPercent(0.7042), "70%");
});
