import api, { buildApiUrl } from "./api";

const UPLOAD_TIMEOUT_MS = 0;
const DOWNLOAD_TIMEOUT_MS = 0;

function parseContentDispositionFileName(contentDisposition) {
  if (!contentDisposition) {
    return null;
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1]);
  }

  const asciiMatch = contentDisposition.match(/filename="?([^"]+)"?/i);
  return asciiMatch?.[1] || null;
}

function triggerBrowserDownload(blob, fileName) {
  const objectUrl = window.URL.createObjectURL(blob);
  const link = window.document.createElement("a");
  link.href = objectUrl;
  link.download = fileName;
  window.document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(objectUrl);
}

async function requestBinary(path, fallbackFileName = "document") {
  const response = await api.get(path, {
    responseType: "blob",
    timeout: DOWNLOAD_TIMEOUT_MS,
  });

  const contentType = response.headers["content-type"] || "application/octet-stream";
  const fileName = parseContentDispositionFileName(response.headers["content-disposition"]) || fallbackFileName;
  const blob = response.data instanceof Blob ? response.data : new Blob([response.data], { type: contentType });
  return { blob, fileName, contentType };
}

async function downloadBinary(path, fallbackFileName) {
  const { blob, fileName } = await requestBinary(path, fallbackFileName);
  triggerBrowserDownload(blob, fileName);
}

export async function listDocuments(params = {}) {
  const { data } = await api.get("/documents", { params });
  return data.data;
}

export async function uploadDocument(file, onProgress) {
  const formData = new FormData();
  formData.append("file", file);

  const { data } = await api.post("/documents/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: UPLOAD_TIMEOUT_MS,
    onUploadProgress: (event) => {
      if (!event.total || !onProgress) return;
      onProgress(Math.round((event.loaded * 100) / event.total));
    },
  });

  return data.data;
}

export async function uploadVersion(documentId, file, onProgress) {
  const formData = new FormData();
  formData.append("file", file);

  const { data } = await api.post(`/documents/${documentId}/versions/upload`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: UPLOAD_TIMEOUT_MS,
    onUploadProgress: (event) => {
      if (!event.total || !onProgress) return;
      onProgress(Math.round((event.loaded * 100) / event.total));
    },
  });

  return data.data;
}

export async function getDocument(documentId) {
  const { data } = await api.get(`/documents/${documentId}`);
  return data.data;
}

export async function getDocumentProcessingStatus(documentId) {
  const { data } = await api.get(`/documents/${documentId}/processing-status`);
  return data.data;
}

export async function retryDocumentProcessing(documentId) {
  const { data } = await api.post(`/documents/${documentId}/retry-processing`);
  return data.data;
}

export async function waitForDocumentProcessing(documentId, { onUpdate, intervalMs = 1000, maxAttempts = 180 } = {}) {
  let latestStatus = null;
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    latestStatus = await getDocumentProcessingStatus(documentId);
    onUpdate?.(latestStatus);
    if (latestStatus.status === "COMPLETED" || latestStatus.status === "FAILED") {
      return latestStatus;
    }
    await new Promise((resolve) => window.setTimeout(resolve, intervalMs));
  }

  throw new Error("Processing did not finish in time.");
}

export async function getDocumentViewer(documentId, versionId) {
  const { data } = await api.get(`/documents/${documentId}/viewer`, {
    params: versionId ? { versionId } : {},
  });
  return data.data;
}

export async function downloadDocument(documentId, fallbackFileName = "document") {
  await downloadBinary(`/documents/${documentId}/download`, fallbackFileName);
}

export async function fetchDocumentBlob(documentId) {
  return requestBinary(`/documents/${documentId}/download`);
}

export async function renameDocument(documentId, name) {
  const { data } = await api.patch(`/documents/${documentId}/rename`, { name });
  return data.data;
}

export async function deleteDocument(documentId) {
  await api.delete(`/documents/${documentId}`);
}

export async function listVersions(documentId) {
  const { data } = await api.get(`/documents/${documentId}/versions`);
  return data.data;
}

export async function downloadVersion(versionId, fallbackFileName = "document-version") {
  await downloadBinary(`/versions/${versionId}/download`, fallbackFileName);
}

export async function fetchVersionBlob(versionId) {
  return requestBinary(`/versions/${versionId}/download`);
}

export async function restoreVersion(versionId) {
  const { data } = await api.post(`/versions/${versionId}/restore`);
  return data.data;
}

export async function compareDocumentVersions(documentId, baseVersionId, targetVersionId) {
  const { data } = await api.get(`/documents/${documentId}/versions/compare`, {
    params: { baseVersionId, targetVersionId },
  });
  return data.data;
}

export async function generateSummary(documentId, maxSentences = 3) {
  const { data } = await api.post(`/documents/${documentId}/ai/summary`, null, {
    params: { maxSentences },
  });
  return data.data;
}

export async function generateTags(documentId) {
  const { data } = await api.post(`/documents/${documentId}/ai/tags`);
  return data.data;
}

export async function rewriteDocument(documentId, payload) {
  const { data } = await api.post(`/documents/${documentId}/ai/rewrite`, payload);
  return data.data;
}

export async function askDocument(documentId, payload) {
  const { data } = await api.post(`/documents/${documentId}/ai/ask`, payload);
  return data.data;
}

export async function shareDocument(documentId, payload) {
  const { data } = await api.post(`/documents/${documentId}/collaborators`, payload);
  return data.data;
}

export async function updateCollaborator(documentId, collaboratorId, payload) {
  const { data } = await api.patch(`/documents/${documentId}/collaborators/${collaboratorId}`, payload);
  return data.data;
}

export async function removeCollaborator(documentId, collaboratorId) {
  await api.delete(`/documents/${documentId}/collaborators/${collaboratorId}`);
}

export async function getShareLink(documentId) {
  const { data } = await api.get(`/documents/${documentId}/share-link`);
  return data.data;
}

export async function enableShareLink(documentId, payload = null) {
  const { data } = await api.post(`/documents/${documentId}/share-link`, payload);
  return data.data;
}

export async function disableShareLink(documentId) {
  await api.delete(`/documents/${documentId}/share-link`);
}

export async function getSharedDocument(token) {
  const { data } = await api.get(`/public/share/${token}`);
  return data.data;
}

export async function addDocumentComment(documentId, payload) {
  const { data } = await api.post(`/documents/${documentId}/comments`, payload);
  return data.data;
}

export async function listDocumentComments(documentId) {
  const { data } = await api.get(`/documents/${documentId}/comments`);
  return data.data;
}

export function downloadSharedDocumentUrl(token) {
  return buildApiUrl(`/public/share/${token}/download`);
}
