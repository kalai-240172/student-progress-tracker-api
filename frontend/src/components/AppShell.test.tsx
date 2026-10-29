import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ logout: vi.fn() }),
}));

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
});
