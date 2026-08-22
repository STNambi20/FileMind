import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../app/providers/AuthProvider";
import { useToast } from "../app/providers/ToastProvider";
import AppFooter from "../components/layout/AppFooter";
import BrandMark from "../components/layout/BrandMark";
import AppIcon from "../components/ui/AppIcon";
import { getApiErrorMessage } from "../utils/apiError";

function RegisterPage() {
  const navigate = useNavigate();
  const { register } = useAuth();
  const { showToast } = useToast();
  const [form, setForm] = useState({ fullName: "", email: "", password: "" });
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      if (form.fullName.trim().length < 2) {
        throw new Error("Please enter your full name.");
      }
      if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(form.password)) {
        throw new Error("Password must be at least 8 characters and include uppercase, lowercase, and a number.");
      }

      await register(form);
      showToast({
        title: "Account created",
        description: "Welcome to FileMind.",
      });
      navigate("/dashboard", { replace: true });
    } catch (submitError) {
      setError(submitError instanceof Error && !submitError.response ? submitError.message : getApiErrorMessage(submitError, "Unable to create account."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col">
      <div className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="grid w-full max-w-6xl overflow-hidden rounded-[2rem] border border-slate-200/70 bg-white/90 shadow-panel backdrop-blur lg:grid-cols-[0.98fr_1.02fr]">
          <div className="hidden bg-[linear-gradient(145deg,#06111f_0%,#0f172a_62%,#172554_100%)] p-10 text-white lg:block">
            <BrandMark subtitle="Premium document operations" theme="dark" />
            <h1 className="mt-10 text-4xl font-semibold leading-tight tracking-[-0.05em] text-white">Create a workspace built for storage efficiency and document intelligence.</h1>
          </div>

          <div className="p-8 sm:p-10 lg:p-12">
            <div className="lg:hidden">
              <BrandMark subtitle="Create your workspace" theme="light" />
            </div>
            <p className="section-label mt-8 lg:mt-0">Create Account</p>
            <h2 className="mt-3 text-3xl font-semibold tracking-[-0.04em] text-slate-950">Create your FileMind account</h2>

            <form className="mt-8 grid gap-4" onSubmit={handleSubmit}>
              <label className="block">
                <span className="mb-2 block text-sm font-medium text-slate-700">Full name</span>
                <input
                  className="input"
                  type="text"
                  placeholder="Your name"
                  value={form.fullName}
                  onChange={(event) => setForm((current) => ({ ...current, fullName: event.target.value }))}
                  required
                />
              </label>
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
                  placeholder="Create a secure password"
                  value={form.password}
                  onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                  required
                />
              </label>
              <p className="text-xs text-slate-500">Use at least 8 characters with uppercase, lowercase, and a number.</p>
              {error ? <p className="rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p> : null}
              <button className="button-primary" disabled={submitting} type="submit">
                <AppIcon className="h-4 w-4" name="check" />
                {submitting ? "Creating account..." : "Create account"}
              </button>
            </form>

            <p className="mt-6 text-sm text-slate-500">
              Already registered?{" "}
              <Link className="font-medium text-brand-700" to="/login">
                Go to sign in
              </Link>
            </p>
          </div>
        </div>
      </div>
      <AppFooter />
    </div>
  );
}

export default RegisterPage;
