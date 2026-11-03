import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";

const { logoutMock } = vi.hoisted(() => ({ logoutMock: vi.fn() }));

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ logout: logoutMock }),
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("application navigation", () => {
  it("navigates to Academic Progress from the main navigation", async () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<p>Overview content</p>} />
            <Route path="academic" element={<p>Academic page content</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole("link", { name: /Academic progress/ }));
    expect(await screen.findByText("Academic page content")).toBeInTheDocument();
  });

  it("includes the export page in the main navigation", () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<p>Overview content</p>} />
            <Route path="exports" element={<p>Export page content</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: /Export data/ })).toHaveAttribute("href", "/exports");
  });

  it("adds Focus Arena without displacing the existing workspace links", () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<p>Overview content</p>} />
            <Route path="focus" element={<p>Focus Arena content</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: /Overview/ })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: /Daily tasks/ })).toHaveAttribute("href", "/tasks");
    fireEvent.click(screen.getByRole("link", { name: /Focus Arena/ }));
    expect(screen.getByText("Focus Arena content")).toBeInTheDocument();
  });

  it("keeps every navigation item and a separate logout action in the sidebar", () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<p>Overview content</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    const sidebar = document.querySelector(".sidebar");
    const navigation = screen.getByRole("navigation", { name: "Main navigation" });
    const sidebarBottom = sidebar?.querySelector(".sidebar-bottom");

    expect(navigation.querySelectorAll("a")).toHaveLength(10);
    expect(sidebarBottom).toContainElement(screen.getByRole("button", { name: "Logout" }));
    expect(sidebar?.querySelector(".brand")).toBeInTheDocument();
  });

  it("securely logs out and redirects to sign-in", async () => {
    logoutMock.mockResolvedValue(undefined);
    render(
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<p>Overview content</p>} />
          </Route>
          <Route path="/login" element={<p>Sign-in page</p>} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Logout" }));

    expect(await screen.findByText("Sign-in page")).toBeInTheDocument();
    expect(logoutMock).toHaveBeenCalledOnce();
  });

  it("renders a decorative ambient background behind the authenticated application", () => {
    render(
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<p>Overview content</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    const app = document.querySelector(".app-shell");
    const background = document.querySelector(".app-background");
    expect(background).toHaveAttribute("aria-hidden", "true");
    expect(background).toContainElement(document.querySelector(".ambient-orb-one"));
    expect(app?.firstElementChild).toBe(background);
    expect(document.querySelector(".main-column")).toBeInTheDocument();
  });
});
