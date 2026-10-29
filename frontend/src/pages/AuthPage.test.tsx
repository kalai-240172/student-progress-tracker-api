import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../auth/AuthContext";
import { AuthPage } from "./AuthPage";

afterEach(cleanup);

describe("registration page", () => {
  it("validates the required account fields before calling the API", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/register"]}>
          <Routes><Route path="/register" element={<AuthPage mode="register" />} /></Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: /create account/i }));
    await waitFor(() => {
      expect(screen.getByText("Name is required")).toBeInTheDocument();
      expect(screen.getByText("Enter a valid email address")).toBeInTheDocument();
      expect(screen.getByText("Use at least 12 characters")).toBeInTheDocument();
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("navigates from sign-in to account registration", async () => {
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

    fireEvent.click(screen.getByRole("link", { name: "Create an account" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "Create your account" })).toBeInTheDocument());
  });
});
