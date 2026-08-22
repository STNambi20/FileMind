import BrandMark from "./BrandMark";
import { useTheme } from "../../app/providers/ThemeProvider";

function AppFooter() {
  const { resolvedTheme } = useTheme();

  return (
    <footer className="shell-glass border-t px-4 py-4 sm:px-6 lg:px-8">
      <div className="app-text-muted mx-auto flex w-full max-w-6xl flex-col gap-3 text-center text-xs sm:text-sm lg:flex-row lg:items-center lg:justify-between lg:text-left">
        <div className="flex items-center justify-center lg:justify-start">
          <BrandMark compact theme={resolvedTheme === "dark" ? "dark" : "light"} />
        </div>
        <div className="space-y-1 lg:text-right">
          <p>FileMind {"\u2014"} Intelligent Document Management System</p>
          <p>&copy; 2026 Group 25A10</p>
        </div>
      </div>
    </footer>
  );
}

export default AppFooter;
