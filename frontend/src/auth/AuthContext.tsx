import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, clearCredentials, setCredentials } from "../api/client";
import { ApiError } from "../api/client";

interface AuthContextValue {
  isAuthenticated: boolean;
  isChecking: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isChecking, setIsChecking] = useState(true);

  const logout = useCallback(() => {
    clearCredentials();
    setIsAuthenticated(false);
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => {
      clearCredentials();
      setIsAuthenticated(false);
    };
    window.addEventListener("studyflow:unauthorized", handleUnauthorized);
    setIsChecking(false);
    return () => window.removeEventListener("studyflow:unauthorized", handleUnauthorized);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    setCredentials(email, password);
    try {
      await api.listGoals();
      setIsAuthenticated(true);
    } catch (error) {
      clearCredentials();
      if (error instanceof ApiError && error.status === 401) {
        throw new ApiError(401, "Email or password is incorrect.");
      }
      throw error;
    }
  }, []);

  const value = useMemo(
    () => ({ isAuthenticated, isChecking, login, logout }),
    [isAuthenticated, isChecking, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
