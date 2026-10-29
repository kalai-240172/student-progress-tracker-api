import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { DailyTask } from "../types";
import { api } from "../api/client";
import { calendarRange, groupTaskHistory, identifyOverdueTasks, navigateCalendar } from "../tasks/dateUtils";
import { CalendarPage } from "./CalendarPage";
import { StudyHistoryPage } from "./StudyHistoryPage";

vi.mock("../api/client", () => ({
  api: {
    listTasks: vi.fn(),
    getOverdueTasks: vi.fn(),
    getStudyStreaks: vi.fn(),
    updateTask: vi.fn(),
  },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function makeTask(
  id: number,
  plannedDate: string,
  status: DailyTask["status"] = "PENDING",
  actualDurationMinutes: number | null = null,
): DailyTask {
  return {
    id,
    title: `Task ${id}`,
    description: null,
    subject: "Study",
    plannedDate,
    estimatedDurationMinutes: 30,
    actualDurationMinutes,
    status,
    createdAt: `${plannedDate}T08:00:00`,
    updatedAt: `${plannedDate}T08:00:00`,
  };
}

describe("calendar date navigation and history analytics", () => {
  it("calculates Monday-based inclusive week and calendar ranges", () => {
    expect(calendarRange(new Date(2026, 9, 29), "week")).toEqual({
      startDate: "2026-10-26",
      endDate: "2026-11-01",
    });
    const monthRange = calendarRange(new Date(2026, 9, 29), "month");
    expect(monthRange.startDate).toBe("2026-09-28");
    expect(monthRange.endDate).toBe("2026-11-08");
    expect(navigateCalendar(new Date(2026, 9, 29), "month", 1)).toEqual(new Date(2026, 10, 1));
    expect(navigateCalendar(new Date(2026, 9, 29), "week", -1)).toEqual(new Date(2026, 9, 22));
  });

  it("groups date history and calculates inclusive planned, actual, and completion totals", () => {
    const groups = groupTaskHistory([
      makeTask(1, "2026-10-28", "COMPLETED", 20),
      makeTask(2, "2026-10-28", "PENDING"),
      makeTask(3, "2026-10-29", "COMPLETED", 25),
    ]);
    expect(groups).toHaveLength(2);
    expect(groups[0]).toMatchObject({
      date: "2026-10-29",
      taskCount: 1,
      completedCount: 1,
      plannedMinutes: 30,
      actualMinutes: 25,
      completionRate: 100,
    });
    expect(groups[1]).toMatchObject({
      date: "2026-10-28",
      taskCount: 2,
      completedCount: 1,
      plannedMinutes: 60,
      actualMinutes: 20,
      completionRate: 50,
    });
  });

  it("identifies overdue only when planned before today and not completed", () => {
    expect(identifyOverdueTasks([
      makeTask(1, "2026-10-28"),
      makeTask(2, "2026-10-28", "IN_PROGRESS"),
      makeTask(3, "2026-10-28", "COMPLETED"),
      makeTask(4, "2026-10-29"),
      makeTask(5, "2026-10-30"),
    ], "2026-10-29").map((task) => task.id)).toEqual([1, 2]);
  });
});

describe("calendar and study history pages", () => {
  it("navigates periods and selects a date to show its tasks", async () => {
    const today = new Date();
    const day = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
    vi.mocked(api.listTasks).mockResolvedValue([makeTask(11, day, "COMPLETED")]);
    render(<CalendarPage />);

    expect(await screen.findByText("Task 11")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("gridcell", { name: /1 task, 1 completed/ }));
    expect(screen.getByText("Task 11")).toBeInTheDocument();
    const previousRange = vi.mocked(api.listTasks).mock.calls[0][0];
    fireEvent.click(screen.getByRole("button", { name: "Next period" }));
    await waitFor(() => expect(api.listTasks).toHaveBeenCalledTimes(2));
    expect(vi.mocked(api.listTasks).mock.calls[1][0]).not.toEqual(previousRange);
  });

  it("applies inclusive history filters and reschedules an overdue task without changing its ID", async () => {
    const today = new Date();
    const todayString = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
    const overdueTask = makeTask(17, "2026-10-20", "IN_PROGRESS");
    vi.mocked(api.listTasks).mockResolvedValue([makeTask(1, todayString, "COMPLETED", 15)]);
    vi.mocked(api.getOverdueTasks).mockResolvedValue([overdueTask]);
    vi.mocked(api.getStudyStreaks).mockResolvedValue({
      asOfDate: todayString,
      currentStreakDays: 1,
      longestStreakDays: 3,
    });
    vi.mocked(api.updateTask).mockResolvedValue({ ...overdueTask, plannedDate: todayString });
    render(<StudyHistoryPage />);

    expect(await screen.findByText("Task 17")).toBeInTheDocument();
    const start = screen.getByLabelText("Start date");
    const end = screen.getByLabelText("End date");
    fireEvent.change(start, { target: { value: "2026-10-01" } });
    fireEvent.change(end, { target: { value: "2026-10-29" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply date range" }));
    await waitFor(() => expect(api.listTasks).toHaveBeenLastCalledWith({
      startDate: "2026-10-01",
      endDate: "2026-10-29",
    }));

    const newDate = screen.getByLabelText("New planned date for Task 17");
    fireEvent.change(newDate, { target: { value: todayString } });
    fireEvent.click(screen.getByRole("button", { name: "Reschedule" }));
    await waitFor(() => expect(api.updateTask).toHaveBeenCalledWith(17, {
      title: overdueTask.title,
      description: overdueTask.description,
      subject: overdueTask.subject,
      plannedDate: todayString,
      estimatedDurationMinutes: overdueTask.estimatedDurationMinutes,
    }));
  });
});
