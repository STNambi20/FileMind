export function documentTypeLabel(mediaType) {
  if (mediaType === "application/pdf") return "PDF";
  if (mediaType === "application/vnd.openxmlformats-officedocument.wordprocessingml.document") return "DOCX";
  if (mediaType === "text/plain") return "TXT";
  if (mediaType === "image/png") return "PNG";
  if (mediaType === "image/jpeg") return "JPG";
  if (mediaType === "image/gif") return "GIF";
  if (mediaType === "image/bmp") return "BMP";
  return mediaType || "File";
}

export function formatVersionOrigin(origin) {
  const labels = {
    UPLOAD: "Upload",
    RESTORE: "Restore",
    AI_REWRITE: "AI rewrite",
  };
  return labels[origin] || origin || "Update";
}

function humanizeActionLabel(value) {
  if (!value) return "Activity";
  if (/^[A-Z_]+$/.test(value)) {
    return value
      .toLowerCase()
      .split("_")
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(" ");
  }
  return value;
}

export function groupActivitiesByDay(items) {
  if (!items?.length) {
    return [];
  }

  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const yesterdayStart = todayStart - 24 * 60 * 60 * 1000;

  const groups = {
    Today: [],
    Yesterday: [],
    Earlier: [],
  };

  items.forEach((item) => {
    const createdAt = new Date(item.createdAt).getTime();
    if (createdAt >= todayStart) {
      groups.Today.push(item);
    } else if (createdAt >= yesterdayStart) {
      groups.Yesterday.push(item);
    } else {
      groups.Earlier.push(item);
    }
  });

  return Object.entries(groups)
    .filter(([, groupedItems]) => groupedItems.length)
    .map(([label, groupedItems]) => ({ label, items: groupedItems }));
}

export function formatActivityTime(value) {
  if (!value) return "";
  return new Date(value).toLocaleTimeString([], {
    hour: "numeric",
    minute: "2-digit",
  });
}

export function resolveActivityMeta(item) {
  const title = humanizeActionLabel(item.action);

  if (title.includes("viewed") || title.includes("Viewed")) {
    return { title, icon: "eye", iconTone: "text-cyan-600" };
  }
  if (title.includes("Summary")) {
    return { title, icon: "summary", iconTone: "text-brand-600" };
  }
  if (title.includes("Tags")) {
    return { title, icon: "tag", iconTone: "text-sky-600" };
  }
  if (title.includes("Rewrite")) {
    return { title, icon: "spark", iconTone: "text-violet-600" };
  }
  if (title.includes("restored") || title.includes("Restored")) {
    return { title, icon: "restore", iconTone: "text-emerald-600" };
  }
  if (title.includes("deleted") || title.includes("Deleted")) {
    return { title, icon: "close", iconTone: "text-rose-600" };
  }
  if (title.includes("shared") || title.includes("Access")) {
    return { title, icon: "share", iconTone: "text-amber-600" };
  }
  if (title.includes("link")) {
    return { title, icon: "link", iconTone: "text-cyan-600" };
  }
  if (title.includes("Sign")) {
    return { title, icon: "user", iconTone: "text-slate-700" };
  }
  if (title.includes("Version")) {
    return { title, icon: "restore", iconTone: "text-emerald-600" };
  }
  if (title.includes("uploaded") || title.includes("Uploaded")) {
    return { title, icon: "upload", iconTone: "text-brand-600" };
  }

  return { title, icon: "activity", iconTone: "text-slate-700" };
}
