import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api/client";
import type { AcademicYear, Semester } from "../types";
import { ExportsPage } from "./ExportsPage";

vi.mock("../api/client", () => ({
  api: {
    listAcademicYears: vi.fn(),
    listSemesters: vi.fn(),
    exportStudyHistory: vi.fn(),
    exportTasks: vi.fn(),
    exportAcademic: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}));

const year: AcademicYear = {
  id: 8,
  label: "2025-2026",
  startDate: null,
  endDate: null,
  createdAt: "2025-01-01T00:00:00",
  updatedAt: "2025-01-01T00:00:00",
};

const semester: Semester = {
  id: 17,
  academicYearId: year.id,
  label: "Semester 2",
  startDate: null,
  endDate: null,
  createdAt: "2025-01-01T00:00:00",
  updatedAt: "2025-01-01T00:00:00",
};

const file = { blob: new Blob(["csv"], { type: "text/csv" }), filename: "study-history.csv" };

beforeEach(() => {
  vi.mocked(api.listAcademicYears).mockResolvedValue([year]);
  vi.mocked(api.listSemesters).mockResolvedValue([semester]);
  vi.mocked(api.exportStudyHistory).mockResolvedValue(file);
  vi.mocked(api.exportTasks).mockResolvedValue({ ...file, filename: "tasks.csv" });
  vi.mocked(api.exportAcademic).mockResolvedValue({ ...file, filename: "academic-results.csv" });
  vi.stubGlobal("URL", {
    createObjectURL: vi.fn(() => "blob:export"),
    revokeObjectURL: vi.fn(),
  });
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("exports page", () => {
  it("uses inclusive date filter values and downloads the returned CSV blob", async () => {
    render(<ExportsPage />);
    await screen.findByRole("option", { name: year.label });
    fireEvent.change(screen.getByLabelText("Export start date"), { target: { value: "2026-04-10" } });
    fireEvent.change(screen.getByLabelText("Export end date"), { target: { value: "2026-04-12" } });
    fireEvent.click(screen.getByRole("button", { name: "Download study history CSV" }));

    await waitFor(() => expect(api.exportStudyHistory).toHaveBeenCalledWith({
      startDate: "2026-04-10",
      endDate: "2026-04-12",
    }));
    expect(URL.createObjectURL).toHaveBeenCalledWith(file.blob);
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:export"), { timeout: 2000 });
    expect(await screen.findByRole("status")).toHaveTextContent(/headers only/);
  });

  it("rejects partial or reversed date ranges before making an export request", async () => {
    render(<ExportsPage />);
    await screen.findByRole("option", { name: year.label });
    fireEvent.change(screen.getByLabelText("Export start date"), { target: { value: "2026-04-12" } });
    fireEvent.change(screen.getByLabelText("Export end date"), { target: { value: "2026-04-10" } });
    fireEvent.click(screen.getByRole("button", { name: "Download daily tasks CSV" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Start date must be on or before end date.");
    expect(api.exportTasks).not.toHaveBeenCalled();
  });

  it("applies academic year and semester filters", async () => {
    render(<ExportsPage />);
    fireEvent.change(await screen.findByLabelText("Academic year filter"), { target: { value: String(year.id) } });
    await waitFor(() => expect(api.listSemesters).toHaveBeenCalledWith(year.id));
    fireEvent.change(await screen.findByLabelText("Semester filter"), { target: { value: String(semester.id) } });
    fireEvent.click(screen.getByRole("button", { name: "Download academic CSV" }));

    await waitFor(() => expect(api.exportAcademic).toHaveBeenCalledWith({
      academicYearId: year.id,
      semesterId: semester.id,
    }));
  });

  it("shows a loading state while the file is being prepared", async () => {
    let finishExport: ((download: typeof file) => void) | undefined;
    vi.mocked(api.exportTasks).mockImplementationOnce(() => new Promise((resolve) => {
      finishExport = resolve;
    }));
    render(<ExportsPage />);
    await screen.findByRole("option", { name: year.label });
    fireEvent.click(screen.getByRole("button", { name: "Download daily tasks CSV" }));

    expect(screen.getByRole("button", { name: "Preparing CSV…" })).toBeDisabled();
    finishExport?.({ ...file, filename: "tasks.csv" });
    expect(await screen.findByRole("status")).toHaveTextContent(/Downloaded tasks.csv/);
  });

  it("shows API errors and retries the failed export", async () => {
    vi.mocked(api.exportTasks)
      .mockRejectedValueOnce(new Error("Export failed"))
      .mockResolvedValueOnce({ ...file, filename: "tasks.csv" });
    render(<ExportsPage />);
    await screen.findByRole("option", { name: year.label });
    fireEvent.click(screen.getByRole("button", { name: "Download daily tasks CSV" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Export failed");
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByRole("status")).toHaveTextContent(/Downloaded tasks.csv/);
    expect(api.exportTasks).toHaveBeenCalledTimes(2);
  });
});
