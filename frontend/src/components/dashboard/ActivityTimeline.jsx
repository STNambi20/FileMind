import AppIcon from "../ui/AppIcon";
import { formatActivityTime, groupActivitiesByDay, resolveActivityMeta } from "../../utils/productPresentation";

function ActivityTimeline({ items }) {
  const groups = groupActivitiesByDay(items);

  if (!groups.length) {
    return (
      <div className="empty-state">
        <div className="empty-state-icon">
          <AppIcon className="h-5 w-5" name="activity" />
        </div>
        <p className="app-text-primary mt-4 text-base font-medium">No activity recorded yet</p>
        <p className="app-text-muted mt-2 text-sm">New uploads, version work, AI actions, and access updates will appear here.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {groups.map((group) => (
        <section key={group.label}>
          <p className="app-text-muted mb-4 text-xs font-semibold uppercase tracking-[0.22em]">{group.label}</p>
          <div className="relative space-y-4 sm:pl-4">
            <div className="absolute bottom-3 left-[1.36rem] top-3 hidden w-px bg-slate-200 sm:block" />
            {group.items.map((item) => {
              const meta = resolveActivityMeta(item);
              return (
                <div key={item.id} className="relative flex gap-4 sm:pl-2">
                  <div className="app-icon-badge relative z-10 shrink-0">
                    <AppIcon className={`h-4.5 w-4.5 ${meta.iconTone}`} name={meta.icon} />
                  </div>
                  <div className="app-surface-elevated min-w-0 flex-1 rounded-3xl border px-4 py-4 shadow-sm sm:px-5">
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div className="min-w-0">
                        <p className="app-text-primary font-medium">{meta.title}</p>
                        <p className="app-text-secondary mt-1 break-words text-sm">{item.details}</p>
                      </div>
                      <p className="app-text-muted shrink-0 text-xs uppercase tracking-[0.16em]">{formatActivityTime(item.createdAt)}</p>
                    </div>
                    {item.actorName ? <p className="app-text-muted mt-3 text-xs">{item.actorName}</p> : null}
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      ))}
    </div>
  );
}

export default ActivityTimeline;
