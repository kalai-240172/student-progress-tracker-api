import { z } from "zod";
import { useForm } from "react-hook-form";
import { ArrowRight, BookOpenCheck, LockKeyhole } from "lucide-react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { Button, FieldError } from "../components/ui";

const loginSchema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});
const registrationSchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(100, "Use at most 100 characters"),
  email: z.string().email("Enter a valid email address").max(254),
  password: z
    .string()
    .min(12, "Use at least 12 characters")
    .max(72, "Use at most 72 characters")
    .refine((value) => new TextEncoder().encode(value).length <= 72, "Password exceeds the 72-byte limit"),
});

type LoginFields = z.infer<typeof loginSchema>;
type RegistrationFields = z.infer<typeof registrationSchema>;

export function AuthPage({ mode }: { mode: "login" | "register" }) {
  const isRegister = mode === "register";
  const { isAuthenticated, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [formError, setFormError] = useState("");
  const [registered, setRegistered] = useState(false);

  const loginForm = useForm<LoginFields>();
  const registerForm = useForm<RegistrationFields>();

  useEffect(() => {
    setRegistered(false);
    setFormError("");
  }, [mode]);

  if (isAuthenticated) return <Navigate to="/" replace />;

  const submitLogin = loginForm.handleSubmit(async (values) => {
    setFormError("");
    const parsed = loginSchema.safeParse(values);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof LoginFields;
        if (key) loginForm.setError(key, { message: issue.message });
      }
      return;
    }
    try {
      await login(parsed.data.email, parsed.data.password);
      navigate((location.state as { from?: string } | null)?.from || "/", { replace: true });
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Unable to sign in.");
    }
  });

  const submitRegistration = registerForm.handleSubmit(async (values) => {
    setFormError("");
    const parsed = registrationSchema.safeParse(values);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof RegistrationFields;
        if (key) registerForm.setError(key, { message: issue.message });
      }
      return;
    }
    try {
      await api.register(parsed.data);
      setRegistered(true);
    } catch (error) {
      if (error instanceof ApiError) {
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field in parsed.data) registerForm.setError(field as keyof RegistrationFields, { message });
        }
        setFormError(error.message);
      } else {
        setFormError(error instanceof Error ? error.message : "Unable to create your account.");
      }
    }
  });

  return (
    <main className="auth-page">
      <div className="auth-brand">
        <span className="brand-mark"><BookOpenCheck size={20} /></span>
        <span>studyflow<span className="brand-period">.</span></span>
      </div>
      <div className="auth-content">
        <div className="auth-intro">
          <span className="auth-kicker"><span className="kicker-dot" /> YOUR PERSONAL LEARNING SPACE</span>
          <h1>{isRegister ? "Make room for\nwhat you’re learning." : "Welcome back\nto your progress."}</h1>
          <p>Set meaningful goals, capture the work you do, and see how consistency adds up.</p>
        </div>
        <section className="auth-card" aria-labelledby="auth-title">
          <div className="auth-card-heading">
            <div className="auth-card-icon"><LockKeyhole size={19} /></div>
            <div>
              <h2 id="auth-title">{isRegister ? "Create your account" : "Sign in to Studyflow"}</h2>
              <p>{isRegister ? "A private space for your learning journey." : "Your goals and records are waiting."}</p>
            </div>
          </div>
          {registered ? (
            <div className="registered-state" role="status">
              <div className="registered-check">✓</div>
              <h3>Account created</h3>
              <p>Your account is ready. Sign in with your email and password to continue.</p>
              <Button onClick={() => navigate("/login", { replace: true })}>Go to sign in <ArrowRight size={16} /></Button>
            </div>
          ) : isRegister ? (
            <form className="auth-form" onSubmit={(event) => void submitRegistration(event)}>
              {formError && <div className="alert alert-error" role="alert">{formError}</div>}
              <label className="field">
                <span>Your name</span>
                <input autoComplete="name" {...registerForm.register("name")} maxLength={100} />
                <FieldError>{registerForm.formState.errors.name?.message}</FieldError>
              </label>
              <label className="field">
                <span>Email address</span>
                <input type="email" autoComplete="email" {...registerForm.register("email")} />
                <FieldError>{registerForm.formState.errors.email?.message}</FieldError>
              </label>
              <label className="field">
                <span>Password</span>
                <input type="password" autoComplete="new-password" {...registerForm.register("password")} />
                <small>12–72 characters</small>
                <FieldError>{registerForm.formState.errors.password?.message}</FieldError>
              </label>
              <Button type="submit" className="auth-submit" disabled={registerForm.formState.isSubmitting}>
                {registerForm.formState.isSubmitting ? "Creating account…" : "Create account"} <ArrowRight size={17} />
              </Button>
            </form>
          ) : (
            <form className="auth-form" onSubmit={(event) => void submitLogin(event)}>
              {formError && <div className="alert alert-error" role="alert">{formError}</div>}
              <label className="field">
                <span>Email address</span>
                <input type="email" autoComplete="username" {...loginForm.register("email")} />
                <FieldError>{loginForm.formState.errors.email?.message}</FieldError>
              </label>
              <label className="field">
                <span>Password</span>
                <input type="password" autoComplete="current-password" {...loginForm.register("password")} />
                <FieldError>{loginForm.formState.errors.password?.message}</FieldError>
              </label>
              <Button type="submit" className="auth-submit" disabled={loginForm.formState.isSubmitting}>
                {loginForm.formState.isSubmitting ? "Signing in…" : "Sign in"} <ArrowRight size={17} />
              </Button>
            </form>
          )}
          {!registered && (
            <p className="auth-switch">
              {isRegister ? "Already have an account?" : "New to Studyflow?"}{" "}
              <Link to={isRegister ? "/login" : "/register"}>{isRegister ? "Sign in" : "Create an account"}</Link>
            </p>
          )}
        </section>
        <p className="auth-security">
          <LockKeyhole size={13} /> Credentials are held in memory only and cleared when you sign out or reload.
        </p>
      </div>
      <div className="auth-decoration" aria-hidden="true">
        <div className="auth-decoration-orbit orbit-one" />
        <div className="auth-decoration-orbit orbit-two" />
        <div className="auth-decoration-dot" />
      </div>
    </main>
  );
}
