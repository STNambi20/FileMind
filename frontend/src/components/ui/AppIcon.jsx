function iconClassName(size, className) {
  return `${size} ${className || ""}`.trim();
}

function AppIcon({ name, className = "", size = "h-4 w-4", strokeWidth = 1.8 }) {
  const props = {
    className: iconClassName(size, className),
    fill: "none",
    stroke: "currentColor",
    strokeLinecap: "round",
    strokeLinejoin: "round",
    strokeWidth,
    viewBox: "0 0 24 24",
  };

  switch (name) {
    case "dashboard":
      return (
        <svg {...props}>
          <path d="M4 5h7v6H4z" />
          <path d="M13 5h7v10h-7z" />
          <path d="M4 13h7v6H4z" />
          <path d="M13 17h7v2h-7z" />
        </svg>
      );
    case "files":
      return (
        <svg {...props}>
          <path d="M7 4h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z" />
          <path d="M14 4v5h5" />
          <path d="M9 14h6" />
          <path d="M9 18h4" />
        </svg>
      );
    case "spark":
      return (
        <svg {...props}>
          <path d="M12 3l1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8z" />
          <path d="M19 4l.7 1.8L21.5 6.5l-1.8.7L19 9l-.7-1.8-1.8-.7 1.8-.7z" />
        </svg>
      );
    case "menu":
      return (
        <svg {...props}>
          <path d="M4 7h16" />
          <path d="M4 12h16" />
          <path d="M4 17h16" />
        </svg>
      );
    case "close":
      return (
        <svg {...props}>
          <path d="M6 6l12 12" />
          <path d="M18 6L6 18" />
        </svg>
      );
    case "logout":
      return (
        <svg {...props}>
          <path d="M10 6H7a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h3" />
          <path d="M14 16l4-4-4-4" />
          <path d="M18 12H9" />
        </svg>
      );
    case "upload":
      return (
        <svg {...props}>
          <path d="M12 16V5" />
          <path d="M8 9l4-4 4 4" />
          <path d="M5 19h14" />
        </svg>
      );
    case "download":
      return (
        <svg {...props}>
          <path d="M12 4v11" />
          <path d="M8 11l4 4 4-4" />
          <path d="M5 19h14" />
        </svg>
      );
    case "restore":
      return (
        <svg {...props}>
          <path d="M4 12a8 8 0 1 0 2.3-5.6" />
          <path d="M4 4v4h4" />
        </svg>
      );
    case "clock":
      return (
        <svg {...props}>
          <circle cx="12" cy="12" r="8" />
          <path d="M12 8v5l3 2" />
        </svg>
      );
    case "storage":
      return (
        <svg {...props}>
          <path d="M4 7c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3z" />
          <path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3" />
          <path d="M4 17c0 1.7 3.6 3 8 3s8-1.3 8-3" />
          <path d="M4 7v10" />
          <path d="M20 7v10" />
        </svg>
      );
    case "activity":
      return (
        <svg {...props}>
          <path d="M4 13h4l2-5 4 10 2-5h4" />
        </svg>
      );
    case "tag":
      return (
        <svg {...props}>
          <path d="M20 10l-8.6 8.6a2 2 0 0 1-2.8 0L3 13V4h9z" />
          <circle cx="8.5" cy="8.5" r="1" />
        </svg>
      );
    case "summary":
      return (
        <svg {...props}>
          <path d="M6 5h12" />
          <path d="M6 10h12" />
          <path d="M6 15h8" />
          <path d="M6 20h5" />
        </svg>
      );
    case "comment":
      return (
        <svg {...props}>
          <path d="M5 6.5A2.5 2.5 0 0 1 7.5 4h9A2.5 2.5 0 0 1 19 6.5v6A2.5 2.5 0 0 1 16.5 15H10l-4 4v-4H7.5A2.5 2.5 0 0 1 5 12.5z" />
        </svg>
      );
    case "share":
      return (
        <svg {...props}>
          <circle cx="18" cy="5" r="2" />
          <circle cx="6" cy="12" r="2" />
          <circle cx="18" cy="19" r="2" />
          <path d="M8 12l8-6" />
          <path d="M8 12l8 6" />
        </svg>
      );
    case "user":
      return (
        <svg {...props}>
          <circle cx="12" cy="8" r="3.5" />
          <path d="M5 20a7 7 0 0 1 14 0" />
        </svg>
      );
    case "search":
      return (
        <svg {...props}>
          <circle cx="11" cy="11" r="6" />
          <path d="M20 20l-4.2-4.2" />
        </svg>
      );
    case "link":
      return (
        <svg {...props}>
          <path d="M10 14l4-4" />
          <path d="M7.5 16.5l-1 1a3 3 0 1 1-4.2-4.2l3-3a3 3 0 0 1 4.2 0" />
          <path d="M16.5 7.5l1-1a3 3 0 0 1 4.2 4.2l-3 3a3 3 0 0 1-4.2 0" />
        </svg>
      );
    case "check":
      return (
        <svg {...props}>
          <path d="M5 12l4 4L19 6" />
        </svg>
      );
    case "eye":
      return (
        <svg {...props}>
          <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6z" />
          <circle cx="12" cy="12" r="3" />
        </svg>
      );
    case "settings":
      return (
        <svg {...props}>
          <path d="M12 3l1.5 2.6 3-.1.8 2.9 2.6 1.5-1.3 2.7 1.3 2.7-2.6 1.5-.8 2.9-3-.1L12 21l-1.5-2.6-3 .1-.8-2.9-2.6-1.5 1.3-2.7-1.3-2.7 2.6-1.5.8-2.9 3 .1z" />
          <circle cx="12" cy="12" r="3" />
        </svg>
      );
    case "sun":
      return (
        <svg {...props}>
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2.5v2.2" />
          <path d="M12 19.3v2.2" />
          <path d="M4.9 4.9l1.6 1.6" />
          <path d="M17.5 17.5l1.6 1.6" />
          <path d="M2.5 12h2.2" />
          <path d="M19.3 12h2.2" />
          <path d="M4.9 19.1l1.6-1.6" />
          <path d="M17.5 6.5l1.6-1.6" />
        </svg>
      );
    case "moon":
      return (
        <svg {...props}>
          <path d="M18 14.5A6.5 6.5 0 0 1 9.5 6a7.5 7.5 0 1 0 8.5 8.5z" />
        </svg>
      );
    case "desktop":
      return (
        <svg {...props}>
          <rect x="3" y="4" width="18" height="12" rx="2" />
          <path d="M8 20h8" />
          <path d="M12 16v4" />
        </svg>
      );
    case "dot":
      return (
        <svg viewBox="0 0 8 8" className={iconClassName(size, className)} fill="currentColor">
          <circle cx="4" cy="4" r="4" />
        </svg>
      );
    default:
      return (
        <svg {...props}>
          <path d="M5 5h14v14H5z" />
        </svg>
      );
  }
}

export default AppIcon;
