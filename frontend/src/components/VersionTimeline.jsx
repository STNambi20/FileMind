function VersionTimeline({ versions }) {
  return (
    <div className="card">
      <div className="space-y-6">
        {versions.map((version, index) => (
          <div key={version.id} className="flex gap-4">
            <div className="flex flex-col items-center">
              <div className="h-3 w-3 rounded-full bg-brand-500" />
              {index < versions.length - 1 ? <div className="mt-2 h-full w-px bg-slate-200" /> : null}
            </div>
            <div className="flex-1 pb-6">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-base font-semibold text-slate-900">{version.label}</p>
                <p className="text-sm text-slate-500">{version.updatedAt}</p>
              </div>
              <p className="mt-1 text-sm text-slate-600">Edited by {version.updatedBy}</p>
              <p className="mt-3 text-sm leading-6 text-slate-600">{version.notes}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default VersionTimeline;

