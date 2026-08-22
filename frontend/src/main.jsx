import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./app/providers/AuthProvider";
import { ConfirmProvider } from "./app/providers/ConfirmProvider";
import { PreferencesProvider } from "./app/providers/PreferencesProvider";
import { ThemeProvider } from "./app/providers/ThemeProvider";
import { ToastProvider } from "./app/providers/ToastProvider";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ToastProvider>
      <ConfirmProvider>
        <ThemeProvider>
          <AuthProvider>
            <PreferencesProvider>
              <BrowserRouter>
                <App />
              </BrowserRouter>
            </PreferencesProvider>
          </AuthProvider>
        </ThemeProvider>
      </ConfirmProvider>
    </ToastProvider>
  </React.StrictMode>
);
