import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { DailyTask } from "../types";
import { api } from "../api/client";
import { ReportsPage } from "./ReportsPage";
import { buildReportMetrics, nextReportAnchor, previousReportAnchor, reportRange } from "../reports/reportUtils";

vi.mock("../api/client", () => ({
  api: {
    listTasks: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function localDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function makeTask(
  id: number,
  plannedDate: string,
  status: DailyTask["status"],
  actualDurationMinutes: number | null,
  estimatedDurationMinutes = 60,
): DailyTask {
  return {
    id,
    title: `Task ${id}`,
    description: null,
    subject: "Study",
    plannedDate,
    estimatedDurationMinutes,
    actualDurationMinutes,
    status,
    createdAt: `${plannedDate}T09:00:00`,
    updatedAt: `${plannedDate}T09:00:00`,
  };
}

describe("report calculations", () => {
  it("uses inclusive week/month boundaries and excludes future dates", () => {
    const today = new Date(2026, 9, 29);
    expect(reportRange(today, "week", today)).toEqual({ startDate: "2026-10-26", endDate: "2026-10-29" });
    expect(reportRange(today, "month", today)).toEqual({ startDate: "2026-10-01", endDate: "2026-10-29" });
    expect(previousReportAnchor(today, "week")).toEqual(new Date(2026, 9, 22));
    expect(nextReportAnchor(today, "month")).toEqual(new Date(2026, 10, 1));
  });

  it("calculates denominator, planned/recorded time, deduplicates tasks, and handles empty periods", () => {
    const range = { startDate: "2026-10-01", endDate: "2026-10-29" };
    const completed = makeTask(1, "2026-10-01", "COMPLETED", 45);
    const tasks = [
      completed,
      completed,
      makeTask(2, "2026-10-29", "PENDING", null, 30),
      makeTask(3, "2026-10-30", "COMPLETED", 90),
    ];
    expect(buildReportMetrics(tasks, range, "2026-10-29")).toMatchObject({
      totalTasks: 2,
      completedTasks: 1,
      completionRate: 50,
      plannedMinutes: 90,
      actualMinutes: 45,
      days: [
        { date: "2026-10-01", completedTasks: 1, totalTasks: 1, actualMinutes: 45 },
        { date: "2026-10-29", completedTasks: 0, totalTasks: 1, actualMinutes: 0 },
      ],
    });
    expect(buildReportMetrics([], range, "2026-10-29")).toMatchObject({
      totalTasks: 0,
      completedTasks: 0,
      completionRate: 0,
      actualMinutes: 0,
      plannedMinutes: 0,
      days: [],
    });
  });
});

describe("reports page", () => {
  it("shows loading, then report metrics and applies an inclusive custom date range", async () => {
    let resolveTasks: ((value: DailyTask[]) => void) | undefined;
    vi.mocked(api.listTasks).mockImplementationOnce(() => new Promise((resolve) => {
      resolveTasks = resolve;
    }));
    render(<ReportsPage />);
    expect(screen.getByRole("status")).toHaveTextContent("Calculating your reports");

    const today = localDate(new Date());
    resolveTasks?.([makeTask(1, today, "COMPLETED", 35, 45), makeTask(2, today, "PENDING", null, 30)]);
    expect(await screen.findByText("Task completion rate")).toBeInTheDocument();
    expect(screen.getByText("50.0%")).toBeInTheDocument();
    expect(screen.getByText("35 min")).toBeInTheDocument();
    expect(screen.getByText(/Completed ÷ all tasks in this period/)).toBeInTheDocument();
    expect(vi.mocked(api.listTasks).mock.calls[0][0]).toMatchObject({
      startDate: expect.any(String),
      endDate: today,
    });

    vi.mocked(api.listTasks).mockResolvedValue([]);
    fireEvent.click(screen.getByRole("button", { name: "Custom" }));
    const range = new Date();
    range.setDate(range.getDate() - 3);
    const startDate = localDate(range);
    fireEvent.change(screen.getByLabelText("Report start date"), { target: { value: startDate } });
    fireEvent.change(screen.getByLabelText("Report end date"), { target: { value: today } });
    fireEvent.click(screen.getByRole("button", { name: "Apply range" }));
    await waitFor(() => expect(api.listTasks).toHaveBeenLastCalledWith({ startDate, endDate: today }));
    expect(await screen.findByText("No task data for this period")).toBeInTheDocument();
  });

  it("shows a clear error with retry when the report request fails", async () => {
    vi.mocked(api.listTasks).mockRejectedValueOnce(new Error("Report unavailable"));
    render(<ReportsPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Report unavailable");
    vi.mocked(api.listTasks).mockResolvedValueOnce([]);
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByText("No task data for this period")).toBeInTheDocument();
  });

  it("rejects a future-ending custom range", async () => {
    vi.mocked(api.listTasks).mockResolvedValue([]);
    render(<ReportsPage />);
    await screen.findByRole("button", { name: "Custom" });
    fireEvent.click(screen.getByRole("button", { name: "Custom" }));
    fireEvent.change(screen.getByLabelText("Report end date"), { target: { value: "2999-01-01" } });
    fireEvent.submit(screen.getByLabelText("Report end date").closest("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("Reports cannot include future dates");
  });
});
