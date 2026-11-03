import { z } from "zod";
import { useForm } from "react-hook-form";
import {
  ArrowRight,
  BookOpenCheck,
  Eye,
  EyeOff,
  LockKeyhole,
  Mail,
} from "lucide-react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { api, ApiError } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { Button, FieldError, LoadingState } from "../components/ui";

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
  const { isAuthenticated, isChecking, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [formError, setFormError] = useState("");
  const [registered, setRegistered] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const loginForm = useForm<LoginFields>();
  const registerForm = useForm<RegistrationFields>();

  useEffect(() => {
    setRegistered(false);
    setFormError("");
  }, [mode]);

  if (isAuthenticated) return <Navigate to="/" replace />;
  if (isChecking) return <LoadingState />;

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
    <main className={`auth-page ${isRegister ? "auth-page-register" : "auth-page-login"}`}>
      <div className="auth-content">
        <div className="auth-brand">
          <span className="brand-mark"><BookOpenCheck size={20} /></span>
          <span>StudyFlow<span className="brand-period">.</span></span>
        </div>
        <div className="auth-intro">
          {isRegister && <span className="auth-kicker"><span className="kicker-dot" /> YOUR PERSONAL LEARNING SPACE</span>}
          <h1>{isRegister ? "Make room for\nwhat you’re learning." : "Welcome Back!"}</h1>
          {isRegister
            ? <p>Set meaningful goals, capture the work you do, and see how consistency adds up.</p>
            : <p>Continue your journey towards better learning.</p>}
        </div>
        <section className="auth-card" aria-labelledby="auth-title">
          <div className="auth-card-heading">
            <div className="auth-card-icon"><LockKeyhole size={19} /></div>
            <div>
              <h2 id="auth-title">{isRegister ? "Create your account" : "Sign in"}</h2>
              {isRegister && <p>A private space for your learning journey.</p>}
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
                <div className="password-input-wrap">
                  <input type={showPassword ? "text" : "password"} autoComplete="new-password" {...registerForm.register("password")} />
                  <button
                    className="password-visibility-button"
                    type="button"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    aria-pressed={showPassword}
                    onClick={() => setShowPassword((visible) => !visible)}
                  >
                    {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                  </button>
                </div>
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
                <div className="auth-input-wrap">
                  <Mail className="auth-input-icon" size={18} aria-hidden="true" />
                  <input type="email" autoComplete="username" {...loginForm.register("email")} />
                </div>
                <FieldError>{loginForm.formState.errors.email?.message}</FieldError>
              </label>
              <label className="field">
                <span>Password</span>
                <div className="password-input-wrap">
                  <LockKeyhole className="auth-input-icon" size={18} aria-hidden="true" />
                  <input type={showPassword ? "text" : "password"} autoComplete="current-password" {...loginForm.register("password")} />
                  <button
                    className="password-visibility-button"
                    type="button"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    aria-pressed={showPassword}
                    onClick={() => setShowPassword((visible) => !visible)}
                  >
                    {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                  </button>
                </div>
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
          <LockKeyhole size={13} /> Your sign-in is protected by a secure server session.
        </p>
      </div>
      {!isRegister && (
        <aside className="auth-art-panel" aria-hidden="true">
          <div className="auth-art-scene">
            <span className="auth-art-halo auth-art-halo-back" />
            <span className="auth-art-orbit auth-art-orbit-one" />
            <span className="auth-art-orbit auth-art-orbit-two" />
            <span className="auth-art-glass auth-art-glass-left" />
            <span className="auth-art-glass auth-art-glass-right" />
            <span className="auth-art-crystal auth-art-crystal-top" />
            <span className="auth-art-crystal auth-art-crystal-bottom" />
            <span className="auth-art-core">
              <span className="auth-art-core-shine" />
            </span>
            <span className="auth-art-light auth-art-light-one" />
            <span className="auth-art-light auth-art-light-two" />
            <span className="auth-art-light auth-art-light-three" />
          </div>
        </aside>
      )}
    </main>
  );
}
