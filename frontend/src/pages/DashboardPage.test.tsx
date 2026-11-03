import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api/client";
import type { DailyTask, Goal, ProgressLog, ProgressSummary, StudentRecord } from "../types";
import { DashboardPage } from "./DashboardPage";

vi.mock("../api/client", () => ({
  api: {
    initializeSampleData: vi.fn(),
    listGoals: vi.fn(),
    listTasks: vi.fn(),
    listRecords: vi.fn(),
    getTodayTaskSummary: vi.fn(),
    getProgress: vi.fn(),
    listLogs: vi.fn(),
  },
}));

vi.mock("../components/DashboardCharts", () => ({
  DashboardCharts: () => <div data-testid="dashboard-charts" />,
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function localDateValue(date: Date): string {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

function makeGoal(id: number, title: string): Goal {
  return {
    id,
    title,
    description: null,
    category: "STUDY",
    targetDate: null,
    targetDurationMinutes: 300,
    preferredWeekdays: [],
    status: "IN_PROGRESS",
    createdAt: "2026-10-01T10:00:00",
    updatedAt: "2026-10-01T10:00:00",
  };
}

function makeSummary(goalId: number, goalTitle: string, target: number, recorded: number): ProgressSummary {
  return {
    goalId,
    goalTitle,
    targetDurationMinutes: target,
    totalRecordedMinutes: recorded,
    remainingMinutes: Math.max(target - recorded, 0),
    progressPercentage: Math.min((recorded / target) * 100, 100),
    totalLogCount: 1,
    currentWeekMinutes: recorded,
    currentMonthMinutes: recorded,
    mostRecentActivityDate: "2026-11-02",
  };
}

function makeTask(
  id: number,
  title: string,
  plannedDate: string,
  status: DailyTask["status"],
  actualDurationMinutes: number | null,
): DailyTask {
  return {
    id,
    title,
    description: null,
    subject: "Mathematics",
    plannedDate,
    estimatedDurationMinutes: 45,
    actualDurationMinutes,
    status,
    createdAt: `${plannedDate}T08:00:00`,
    updatedAt: `${plannedDate}T08:00:00`,
  };
}

function makeLog(id: number, goalId: number, activityDate: string, summary: string): ProgressLog {
  return {
    id,
    goalId,
    activityDate,
    timeSpentMinutes: 40,
    summary,
    notes: null,
    createdAt: `${activityDate}T12:00:00`,
    updatedAt: `${activityDate}T12:00:00`,
  };
}

describe("dashboard", () => {
  beforeEach(() => {
    const today = new Date();
    const todayValue = localDateValue(today);
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);
    const goals = [makeGoal(1, "Data structures"), makeGoal(2, "Database project")];
    const summaries = [
      makeSummary(1, "Data structures", 300, 150),
      makeSummary(2, "Database project", 400, 100),
    ];
    const records = Array.from({ length: 5 }, (_, index) => ({
      id: index + 1,
      title: `Academic record ${index + 1}`,
    })) as StudentRecord[];
    const tasks = [
      makeTask(1, "Complete practice set", todayValue, "COMPLETED", 40),
      makeTask(2, "Review lecture notes", todayValue, "PENDING", null),
      makeTask(3, "Prepare a study outline", localDateValue(tomorrow), "PENDING", null),
    ];

    vi.mocked(api.initializeSampleData).mockResolvedValue();
    vi.mocked(api.listGoals).mockResolvedValue(goals);
    vi.mocked(api.listTasks).mockResolvedValue(tasks);
    vi.mocked(api.listRecords).mockResolvedValue(records);
    vi.mocked(api.getTodayTaskSummary).mockResolvedValue({
      date: todayValue,
      taskCount: 2,
      completedCount: 1,
      pendingOrInProgressCount: 1,
      plannedMinutes: 90,
      actualMinutes: 40,
    });
    vi.mocked(api.getProgress).mockImplementation(async (id) => summaries[id - 1]);
    vi.mocked(api.listLogs).mockImplementation(async (goalId) => [
      makeLog(goalId, goalId, todayValue, goalId === 1 ? "Mapped arrays and lists" : "Reviewed database keys"),
    ]);
  });

  it("uses saved records, task summaries, goals, and upcoming sessions for dashboard values", async () => {
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>,
    );

    const recordsMetric = await screen.findByText("Student records");
    expect(recordsMetric.nextElementSibling).toHaveTextContent("5");
    expect(screen.getByText("Tasks completed").nextElementSibling).toHaveTextContent("1");
    expect(screen.getByText("Tasks pending").nextElementSibling).toHaveTextContent("2");
    expect(screen.getByText("Study time today").nextElementSibling).toHaveTextContent("40 min");
    expect(screen.getByText("Goal completion").nextElementSibling).toHaveTextContent("35.7%");
    expect(screen.getByText("Prepare a study outline")).toBeInTheDocument();
    expect(screen.getByText("Mapped arrays and lists")).toBeInTheDocument();
    expect(await screen.findByTestId("dashboard-charts")).toBeInTheDocument();
    expect(api.initializeSampleData).toHaveBeenCalledOnce();
  });
});
