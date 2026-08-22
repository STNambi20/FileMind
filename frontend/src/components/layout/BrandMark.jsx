import AppIcon from "../ui/AppIcon";

function BrandMark({ theme = "dark", compact = false, subtitle }) {
  const dark = theme === "dark";
  const textPrimary = dark ? "text-white" : "text-slate-950";
  const textSecondary = dark ? "text-slate-400" : "text-slate-500";
  const iconShell = dark
    ? "bg-gradient-to-br from-brand-400 via-brand-500 to-brand-700 text-white shadow-[0_16px_34px_rgba(35,78,212,0.35)]"
    : "bg-gradient-to-br from-brand-100 via-brand-200 to-brand-400 text-brand-900 shadow-[0_16px_34px_rgba(35,78,212,0.18)]";

  return (
    <div className="flex items-center gap-3">
      <div className={`flex h-11 w-11 items-center justify-center rounded-2xl ${iconShell}`}>
        <div className="relative">
          <AppIcon className="h-5 w-5" name="files" strokeWidth={1.9} />
          <span className="absolute -right-1 -top-1 flex h-3 w-3 items-center justify-center rounded-full bg-white/90 text-brand-600">
            <AppIcon className="h-2 w-2" name="spark" strokeWidth={2.1} />
          </span>
        </div>
      </div>
      <div className="min-w-0">
        <p className={`text-[1.08rem] font-semibold tracking-[-0.03em] ${textPrimary}`}>
          <span>File</span>
          <span className="bg-gradient-to-r from-brand-400 via-brand-500 to-sky-400 bg-clip-text text-transparent">Mind</span>
        </p>
        {!compact ? (
          <p className={`text-xs ${textSecondary}`}>
            {subtitle || "Intelligent document management"}
          </p>
        ) : null}
      </div>
    </div>
  );
}

export default BrandMark;
