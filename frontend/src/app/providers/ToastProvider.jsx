import { createContext, useContext, useMemo, useRef, useState } from "react";

const ToastContext = createContext(null);

function toneStyles(tone) {
  if (tone === "error") {
    return "border-rose-200 bg-rose-50 text-rose-800";
  }
  if (tone === "warning") {
    return "border-amber-200 bg-amber-50 text-amber-800";
  }
  return "border-emerald-200 bg-emerald-50 text-emerald-800";
}

export function ToastProvider({ children }) {
  const idRef = useRef(0);
  const [toasts, setToasts] = useState([]);

  function dismissToast(id) {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }

  function showToast({ title, description, tone = "success", duration = 3200 }) {
    const id = ++idRef.current;
    setToasts((current) => [...current, { id, title, description, tone }]);
    window.setTimeout(() => dismissToast(id), duration);
  }

  const value = useMemo(() => ({ showToast, dismissToast }), []);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed right-4 top-4 z-50 flex w-full max-w-sm flex-col gap-3">
        {toasts.map((toast) => (
          <div key={toast.id} className={`pointer-events-auto rounded-2xl border p-4 shadow-panel ${toneStyles(toast.tone)}`}>
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-semibold">{toast.title}</p>
                {toast.description ? <p className="mt-1 text-sm opacity-90">{toast.description}</p> : null}
              </div>
              <button className="text-sm font-medium opacity-70 transition hover:opacity-100" onClick={() => dismissToast(toast.id)} type="button">
                Close
              </button>
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return context;
}
