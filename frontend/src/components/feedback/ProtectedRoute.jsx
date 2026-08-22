import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../app/providers/AuthProvider";

function ProtectedRoute() {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="card max-w-md text-center">
          <p className="text-sm font-medium text-brand-700">Loading workspace</p>
          <p className="mt-2 text-sm text-slate-500">Checking your session and preparing FileMind.</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}

export default ProtectedRoute;
