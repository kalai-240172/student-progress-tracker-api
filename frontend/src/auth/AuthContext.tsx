import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, ApiError } from "../api/client";

interface AuthContextValue {
  isAuthenticated: boolean;
  isChecking: boolean;
  sessionError: string | null;
  retrySession: () => void;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isChecking, setIsChecking] = useState(true);
  const [sessionError, setSessionError] = useState<string | null>(null);

  const [sessionCheck, setSessionCheck] = useState(0);

  useEffect(() => {
    const handleUnauthorized = () => {
      setIsAuthenticated(false);
      setSessionError(null);
    };
    window.addEventListener("studyflow:unauthorized", handleUnauthorized);
    return () => window.removeEventListener("studyflow:unauthorized", handleUnauthorized);
  }, []);

  useEffect(() => {
    let active = true;
    setIsChecking(true);
    setSessionError(null);
    void api.currentSession()
      .then(() => {
        if (active) setIsAuthenticated(true);
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError && error.status === 401) {
          setIsAuthenticated(false);
          return;
        }
        setSessionError(error instanceof Error ? error.message : "Unable to verify your session.");
      })
      .finally(() => {
        if (active) setIsChecking(false);
      });
    return () => {
      active = false;
    };
  }, [sessionCheck]);

  const retrySession = useCallback(() => setSessionCheck((check) => check + 1), []);

  const login = useCallback(async (email: string, password: string) => {
    await api.login(email, password);
    setSessionError(null);
    setIsAuthenticated(true);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error;
    }
    setSessionError(null);
    setIsAuthenticated(false);
  }, []);

  const value = useMemo(
    () => ({ isAuthenticated, isChecking, sessionError, retrySession, login, logout }),
    [isAuthenticated, isChecking, sessionError, retrySession, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
