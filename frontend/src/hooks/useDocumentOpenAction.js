import { useNavigate } from "react-router-dom";
import { useToast } from "../app/providers/ToastProvider";
import { usePreferences } from "../app/providers/PreferencesProvider";
import { downloadDocument, downloadVersion } from "../services/documentService";
import { getApiErrorMessage } from "../utils/apiError";

export function useDocumentOpenAction() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { preferences } = usePreferences();

  async function openDocument({ documentId, fileName, versionId = "", currentVersion = true }) {
    if (preferences.defaultOpenMode === "DOWNLOAD") {
      try {
        if (versionId && !currentVersion) {
          await downloadVersion(versionId, fileName || "document-version");
        } else {
          await downloadDocument(documentId, fileName || "document");
        }
        showToast({
          title: "Download started",
          description: "The selected file is being prepared.",
        });
      } catch (requestError) {
        const message = getApiErrorMessage(requestError, "Unable to open this file");
        showToast({
          title: "Open failed",
          description: message,
          tone: "error",
        });
      }
      return;
    }

    if (versionId && !currentVersion) {
      navigate(`/files/${documentId}/view?versionId=${versionId}`);
      return;
    }
    navigate(`/files/${documentId}/view`);
  }

  return {
    defaultOpenMode: preferences.defaultOpenMode,
    openDocument,
    openActionLabel: preferences.defaultOpenMode === "DOWNLOAD" ? "Download" : "Open",
  };
}
