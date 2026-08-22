const WORKSPACE_UPDATED_EVENT = "filemind:workspace-updated";

export function notifyWorkspaceUpdated(detail = {}) {
  window.dispatchEvent(new CustomEvent(WORKSPACE_UPDATED_EVENT, { detail }));
}

export function subscribeWorkspaceUpdates(handler) {
  window.addEventListener(WORKSPACE_UPDATED_EVENT, handler);
  return () => window.removeEventListener(WORKSPACE_UPDATED_EVENT, handler);
}
