import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ logout: vi.fn() }),
}));

afterEach(cleanup);

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
});
