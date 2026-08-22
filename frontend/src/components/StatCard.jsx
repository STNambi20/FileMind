import AppIcon from "./ui/AppIcon";

function StatCard({ label, value, helper, icon = "storage", accent = "bg-slate-100 text-slate-700" }) {
  return (
    <div className="card min-h-[10.5rem]">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="app-text-muted text-sm font-medium">{label}</p>
          <p className="app-text-primary mt-4 text-3xl font-semibold tracking-[-0.04em] sm:text-[2rem]">{value}</p>
        </div>
        <span className={`app-icon-badge shadow-sm ${accent}`}>
          <AppIcon className="h-4.5 w-4.5" name={icon} />
        </span>
      </div>
      {helper ? <p className="app-text-muted mt-3 max-w-[18rem] text-sm leading-6">{helper}</p> : null}
    </div>
  );
}

export default StatCard;
