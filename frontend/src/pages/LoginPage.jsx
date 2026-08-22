import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../app/providers/AuthProvider";
import { useToast } from "../app/providers/ToastProvider";
import AppFooter from "../components/layout/AppFooter";
import BrandMark from "../components/layout/BrandMark";
import AppIcon from "../components/ui/AppIcon";
import { getApiErrorMessage } from "../utils/apiError";

const highlights = ["Deduplicated storage", "Version recovery", "AI-assisted workflows"];

function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const { showToast } = useToast();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      await login(form);
      showToast({
        title: "Signed in",
        description: "Your workspace is ready.",
      });
      navigate(location.state?.from?.pathname || "/dashboard", { replace: true });
    } catch (submitError) {
      setError(getApiErrorMessage(submitError, "Unable to sign in right now."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col">
      <div className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="grid w-full max-w-6xl overflow-hidden rounded-[2rem] border border-slate-200/70 bg-white/90 shadow-panel backdrop-blur lg:grid-cols-[1.04fr_0.96fr]">
          <div className="bg-[linear-gradient(145deg,#06111f_0%,#0f172a_62%,#172554_100%)] p-8 text-white sm:p-10 lg:p-12">
            <BrandMark subtitle="Intelligent document management" theme="dark" />
            <h1 className="mt-10 text-4xl font-semibold leading-tight tracking-[-0.05em] text-white sm:text-[2.65rem]">
              Storage-aware document operations for modern teams.
            </h1>
            <div className="mt-8 flex flex-wrap gap-2">
              {highlights.map((item) => (
                <span key={item} className="label-chip border-white/10 bg-white/10 text-white">
                  {item}
                </span>
              ))}
            </div>
          </div>

          <div className="p-8 sm:p-10 lg:p-12">
            <p className="section-label">Sign In</p>
            <h2 className="mt-3 text-3xl font-semibold tracking-[-0.04em] text-slate-950">Welcome back</h2>

            <form className="mt-8 space-y-4" onSubmit={handleSubmit}>
              <label className="block">
                <span className="mb-2 block text-sm font-medium text-slate-700">Email</span>
                <input
                  className="input"
                  type="email"
                  placeholder="you@company.com"
                  value={form.email}
                  onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
                  required
                />
              </label>
              <label className="block">
                <span className="mb-2 block text-sm font-medium text-slate-700">Password</span>
                <input
                  className="input"
                  type="password"
                  placeholder="Enter your password"
                  value={form.password}
                  onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                  required
                />
              </label>
              {error ? <p className="rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p> : null}
              <button className="button-primary w-full" disabled={submitting} type="submit">
                <AppIcon className="h-4 w-4" name="check" />
                {submitting ? "Signing in..." : "Sign in"}
              </button>
            </form>

            <p className="mt-6 text-sm text-slate-500">
              Need an account?{" "}
              <Link className="font-medium text-brand-700" to="/register">
                Create one
              </Link>
            </p>
          </div>
        </div>
      </div>
      <AppFooter />
    </div>
  );
}

export default LoginPage;
