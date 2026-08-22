import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./app/providers/AuthProvider";
import AppLayout from "./layouts/AppLayout";
import ProtectedRoute from "./components/feedback/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import DashboardPage from "./pages/DashboardPage";
import MyFilesPage from "./pages/MyFilesPage";
import FileDetailsPage from "./pages/FileDetailsPage";
import DocumentViewerPage from "./pages/DocumentViewerPage";
import VersionHistoryPage from "./pages/VersionHistoryPage";
import AIToolsPage from "./pages/AIToolsPage";
import ActivityPage from "./pages/ActivityPage";
import SettingsPage from "./pages/SettingsPage";
import SharedFilePage from "./pages/SharedFilePage";

function App() {
  const { isAuthenticated } = useAuth();

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <LoginPage />}
      />
      <Route
        path="/register"
        element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <RegisterPage />}
      />
      <Route path="/shared/:token" element={<SharedFilePage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/files" element={<MyFilesPage />} />
          <Route path="/files/:fileId" element={<FileDetailsPage />} />
          <Route path="/files/:fileId/view" element={<DocumentViewerPage />} />
          <Route path="/files/:fileId/versions" element={<VersionHistoryPage />} />
          <Route path="/activity" element={<ActivityPage />} />
          <Route path="/ai-tools" element={<AIToolsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Route>
    </Routes>
  );
}

export default App;
