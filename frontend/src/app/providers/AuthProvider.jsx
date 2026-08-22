import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { useToast } from "./ToastProvider";
import { getCurrentUser, login as loginRequest, register as registerRequest } from "../../services/authService";
import { registerUnauthorizedHandler, setAuthToken } from "../../services/api";

const AuthContext = createContext(null);
const STORAGE_KEY = "filemind.accessToken";

export function AuthProvider({ children }) {
  const { showToast } = useToast();
  const [token, setToken] = useState(() => localStorage.getItem(STORAGE_KEY));
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  function clearSession(showExpiredMessage = false) {
    localStorage.removeItem(STORAGE_KEY);
    setAuthToken(null);
    setToken(null);
    setUser(null);
    if (showExpiredMessage) {
      showToast({
        title: "Session expired",
        description: "Please sign in again to continue.",
        tone: "warning",
      });
    }
  }

  useEffect(() => {
    return registerUnauthorizedHandler(() => {
      if (token) {
        clearSession(true);
      }
    });
  }, [token]);

  useEffect(() => {
    setAuthToken(token);
    if (!token) {
      setUser(null);
      setLoading(false);
      return;
    }

    getCurrentUser()
      .then((currentUser) => setUser(currentUser))
      .catch(() => {
        clearSession(false);
      })
      .finally(() => setLoading(false));
  }, [token]);

  async function login(payload) {
    const response = await loginRequest(payload);
    localStorage.setItem(STORAGE_KEY, response.accessToken);
    setAuthToken(response.accessToken);
    setToken(response.accessToken);
    setUser(response.user);
    return response;
  }

  async function register(payload) {
    const response = await registerRequest(payload);
    localStorage.setItem(STORAGE_KEY, response.accessToken);
    setAuthToken(response.accessToken);
    setToken(response.accessToken);
    setUser(response.user);
    return response;
  }

  function logout() {
    clearSession(false);
  }

  function updateCurrentUser(nextUser) {
    setUser(nextUser);
  }

  const value = useMemo(
    () => ({
      token,
      user,
      loading,
      isAuthenticated: Boolean(token && user),
      login,
      register,
      logout,
      updateCurrentUser,
    }),
    [loading, token, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
