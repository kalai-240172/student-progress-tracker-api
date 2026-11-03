import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import { AuthPage } from "./AuthPage";

afterEach(cleanup);

function unauthenticatedResponse() {
  return new Response(JSON.stringify({ status: 401, message: "Authentication is required" }), {
    status: 401,
    headers: { "Content-Type": "application/json" },
  });
}

describe("registration page", () => {
  it("validates the required account fields before calling the API", async () => {
    const fetchMock = vi.fn().mockResolvedValue(unauthenticatedResponse());
    vi.stubGlobal("fetch", fetchMock);
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/register"]}>
          <Routes><Route path="/register" element={<AuthPage mode="register" />} /></Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    fireEvent.click(await screen.findByRole("button", { name: /create account/i }));
    await waitFor(() => {
      expect(screen.getByText("Name is required")).toBeInTheDocument();
      expect(screen.getByText("Enter a valid email address")).toBeInTheDocument();
      expect(screen.getByText("Use at least 12 characters")).toBeInTheDocument();
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("navigates from sign-in to account registration", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(unauthenticatedResponse()));
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/login"]}>
          <Routes>
            <Route path="/login" element={<AuthPage mode="login" />} />
            <Route path="/register" element={<AuthPage mode="register" />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    fireEvent.click(await screen.findByRole("link", { name: "Create an account" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "Create your account" })).toBeInTheDocument());
  });

  it("shows and hides the password without clearing its value", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(unauthenticatedResponse()));
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/login"]}>
          <Routes><Route path="/login" element={<AuthPage mode="login" />} /></Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    const password = await screen.findByLabelText("Password") as HTMLInputElement;
    fireEvent.change(password, { target: { value: "StrongPassword123!" } });
    fireEvent.click(screen.getByRole("button", { name: "Show password" }));
    expect(password).toHaveAttribute("type", "text");
    expect(password).toHaveValue("StrongPassword123!");

    fireEvent.click(screen.getByRole("button", { name: "Hide password" }));
    expect(password).toHaveAttribute("type", "password");
    expect(password).toHaveValue("StrongPassword123!");
  });

  it("logs in with a CSRF-protected session request and navigates to the dashboard", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(unauthenticatedResponse())
      .mockResolvedValueOnce(new Response(JSON.stringify({
        headerName: "X-CSRF-TOKEN",
        token: "login-csrf",
      }), { status: 200, headers: { "Content-Type": "application/json" } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/login"]}>
          <Routes>
            <Route path="/login" element={<AuthPage mode="login" />} />
            <Route path="/" element={<p>Dashboard ready</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    fireEvent.change(await screen.findByLabelText("Email address"), {
      target: { value: "student@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "StrongPassword123!" },
    });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await screen.findByText("Dashboard ready");
    expect(String(fetchMock.mock.calls[2][0])).toContain("/api/auth/login");
    const loginRequest = fetchMock.mock.calls[2][1] as RequestInit;
    expect(new Headers(loginRequest.headers).get("X-CSRF-TOKEN")).toBe("login-csrf");
    expect(loginRequest.credentials).toBe("include");
    expect(JSON.parse(loginRequest.body as string)).toEqual({
      email: "student@example.com",
      password: "StrongPassword123!",
    });
  });

  it("restores a valid server session when the app loads", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ email: "student@example.com" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    ));
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/login"]}>
          <Routes>
            <Route path="/login" element={<AuthPage mode="login" />} />
            <Route path="/" element={<p>Dashboard restored</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    await screen.findByText("Dashboard restored");
  });

  it("renders the split-screen login with abstract art and branded inputs", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(unauthenticatedResponse()));
    const { container } = render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/login"]}>
          <Routes><Route path="/login" element={<AuthPage mode="login" />} /></Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(await screen.findByRole("heading", { name: "Welcome Back!" })).toBeInTheDocument();
    const authContent = container.querySelector(".auth-content");
    expect(authContent).toContainElement(container.querySelector(".auth-intro"));
    expect(authContent).toContainElement(container.querySelector(".auth-card"));
    expect(container.querySelector(".auth-page")).toHaveClass("auth-page-login");
    expect(container.querySelector(".auth-brand")).toHaveTextContent("StudyFlow");
    expect(container.querySelectorAll(".auth-input-icon")).toHaveLength(2);
    expect(await screen.findByText("Continue your journey towards better learning.")).toBeInTheDocument();
    expect(container.querySelector(".auth-art-panel")).toHaveAttribute("aria-hidden", "true");
    expect(container.querySelector(".auth-art-core")).toBeInTheDocument();
    expect(container.querySelectorAll(".auth-art-crystal")).toHaveLength(2);
    expect(screen.queryByText("Track Your Progress")).not.toBeInTheDocument();
    expect(screen.queryByText("Small steps every day lead to big achievements.")).not.toBeInTheDocument();
    expect(container.querySelector(".auth-decoration")).not.toBeInTheDocument();
  });
});
