import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../app/providers/AuthProvider";

function AdminRoute() {
  const { user, isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="card max-w-md text-center">
          <p className="text-sm font-medium text-brand-700">Loading workspace</p>
          <p className="mt-2 text-sm text-slate-500">Preparing the admin console.</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (user?.role !== "ADMIN") {
    return <Navigate to="/dashboard" replace />;
  }

  return <Outlet />;
}

export default AdminRoute;
