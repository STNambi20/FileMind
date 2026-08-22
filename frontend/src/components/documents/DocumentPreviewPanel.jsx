import { useEffect, useState } from "react";
import { fetchDocumentBlob, fetchVersionBlob } from "../../services/documentService";
import { usePreferences } from "../../app/providers/PreferencesProvider";

function DocumentPreviewPanel({ viewer, fileId, compact = false }) {
  const [binaryPreviewUrl, setBinaryPreviewUrl] = useState("");
  const { preferences } = usePreferences();

  useEffect(() => {
    let objectUrl = "";

    async function loadBinaryPreview() {
      if (!viewer || (viewer.previewMode !== "PDF" && viewer.previewMode !== "IMAGE")) {
        setBinaryPreviewUrl("");
        return;
      }

      try {
        const result = viewer.version.currentVersion ? await fetchDocumentBlob(fileId) : await fetchVersionBlob(viewer.version.id);
        objectUrl = window.URL.createObjectURL(result.blob);
        setBinaryPreviewUrl(objectUrl);
      } catch {
        setBinaryPreviewUrl("");
      }
    }

    loadBinaryPreview();

    return () => {
      if (objectUrl) {
        window.URL.revokeObjectURL(objectUrl);
      }
    };
  }, [fileId, viewer]);

  if (!viewer) {
    return null;
  }

  const previewHeight = compact ? "h-[28rem]" : "h-[42rem]";
  const fallbackHeight = compact ? "h-[18rem]" : "h-[24rem]";
  const textWrapClass = preferences.wrapTextForTextFiles ? "whitespace-pre-wrap" : "whitespace-pre overflow-x-auto";

  if (viewer.previewMode === "PDF") {
    return (
      <div className="space-y-4">
        <div className="overflow-hidden rounded-[1.7rem] border border-slate-200 bg-slate-50">
          {binaryPreviewUrl ? (
            <iframe className={`${previewHeight} w-full bg-white`} src={binaryPreviewUrl} title="Document PDF preview" />
          ) : (
            <div className={`flex ${fallbackHeight} items-center justify-center text-sm text-slate-500`}>Preparing PDF preview...</div>
          )}
        </div>
        <div className="rounded-[1.7rem] bg-slate-50 px-5 py-5">
          <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Extracted Text</p>
          <p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-slate-700">
            {viewer.previewText || "No extracted text is available for this PDF."}
          </p>
        </div>
      </div>
    );
  }

  if (viewer.previewMode === "IMAGE") {
    return (
      <div className="space-y-4">
        <div className="overflow-hidden rounded-[1.7rem] border border-slate-200 bg-slate-50 p-4">
          {binaryPreviewUrl ? (
            <img alt={viewer.documentName} className={`${previewHeight} w-full rounded-[1.3rem] object-contain bg-white`} src={binaryPreviewUrl} />
          ) : (
            <div className={`flex ${fallbackHeight} items-center justify-center text-sm text-slate-500`}>Preparing image preview...</div>
          )}
        </div>
        <div className="rounded-[1.7rem] bg-slate-50 px-5 py-5">
          <p className="text-xs uppercase tracking-[0.15em] text-slate-500">Image Details</p>
          <p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-slate-700">
            {viewer.previewText || "No extracted image details are available for this file."}
          </p>
        </div>
      </div>
    );
  }

  if (viewer.previewText) {
    return (
      <div className="rounded-[1.7rem] border border-slate-200 bg-slate-50 px-5 py-5">
        <p className={`${textWrapClass} text-sm leading-7 text-slate-700`}>{viewer.previewText}</p>
        {viewer.previewTruncated ? <p className="mt-4 text-xs font-medium text-slate-500">Preview truncated for faster reading.</p> : null}
      </div>
    );
  }

  return (
    <div className="rounded-[1.7rem] border border-dashed border-slate-200 bg-slate-50 px-5 py-10 text-center text-sm text-slate-500">
      A built-in preview is not available for this version.
    </div>
  );
}

export default DocumentPreviewPanel;
