import { createContext, useContext, useMemo, useState } from "react";

const ConfirmContext = createContext(null);

export function ConfirmProvider({ children }) {
  const [dialog, setDialog] = useState(null);

  function confirm(options) {
    return new Promise((resolve) => {
      setDialog({
        ...options,
        resolve,
      });
    });
  }

  function close(result) {
    if (dialog?.resolve) {
      dialog.resolve(result);
    }
    setDialog(null);
  }

  const value = useMemo(() => ({ confirm }), []);

  return (
    <ConfirmContext.Provider value={value}>
      {children}
      {dialog ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 px-4">
          <div className="w-full max-w-md rounded-[1.75rem] bg-white p-6 shadow-panel">
            <p className="text-sm font-medium uppercase tracking-[0.18em] text-brand-700">{dialog.eyebrow || "Confirm action"}</p>
            <h3 className="mt-3 text-2xl font-semibold text-slate-900">{dialog.title}</h3>
            {dialog.description ? <p className="mt-3 text-sm leading-7 text-slate-600">{dialog.description}</p> : null}
            <div className="mt-6 flex flex-wrap justify-end gap-3">
              <button className="button-secondary" onClick={() => close(false)} type="button">
                {dialog.cancelLabel || "Cancel"}
              </button>
              <button className={dialog.destructive ? "button-danger" : "button-primary"} onClick={() => close(true)} type="button">
                {dialog.confirmLabel || "Confirm"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </ConfirmContext.Provider>
  );
}

export function useConfirm() {
  const context = useContext(ConfirmContext);
  if (!context) {
    throw new Error("useConfirm must be used within ConfirmProvider");
  }
  return context;
}
