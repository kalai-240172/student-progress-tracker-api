import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { DailyTask } from "../types";
import { api } from "../api/client";
import { TasksPage } from "./TasksPage";

vi.mock("../api/client", () => ({
  api: {
    listTasks: vi.fn(),
    getTodayTaskSummary: vi.fn(),
    updateTaskStatus: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const task: DailyTask = {
  id: 7,
  title: "Review recursion",
  description: "Practice examples",
  subject: "Algorithms",
  plannedDate: "2026-10-29",
  estimatedDurationMinutes: 45,
  actualDurationMinutes: null,
  status: "PENDING",
  createdAt: "2026-10-29T08:00:00",
  updatedAt: "2026-10-29T08:00:00",
};

describe("daily tasks page", () => {
  it("renders real task and today's summary and lets the user change status", async () => {
    vi.mocked(api.listTasks).mockResolvedValue([task]);
    vi.mocked(api.getTodayTaskSummary).mockResolvedValue({
      date: "2026-10-29",
      taskCount: 1,
      completedCount: 0,
      pendingOrInProgressCount: 1,
      plannedMinutes: 45,
      actualMinutes: 0,
    });
    vi.mocked(api.updateTaskStatus).mockResolvedValue({ ...task, status: "COMPLETED" });
    render(<MemoryRouter><TasksPage /></MemoryRouter>);

    expect(await screen.findByRole("heading", { name: "Review recursion" })).toBeInTheDocument();
    expect(screen.getByText("45 min", { selector: "strong" })).toBeInTheDocument();
    fireEvent.change(screen.getByRole("combobox", { name: "Status for Review recursion" }), {
      target: { value: "COMPLETED" },
    });

    await waitFor(() => {
      expect(api.updateTaskStatus).toHaveBeenCalledWith(7, "COMPLETED");
      expect(api.listTasks).toHaveBeenCalledTimes(2);
    });
  });
});
