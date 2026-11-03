import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../api/client";
import type { AcademicSubject, AcademicYearSummary, FocusSession, Goal } from "../types";
import { FocusArenaPage } from "./FocusArenaPage";

vi.mock("../api/client", () => ({
  api: {
    initializeSampleData: vi.fn(),
    listAcademicYears: vi.fn(),
    getAcademicYearSummary: vi.fn(),
    listGoals: vi.fn(),
    listFocusSessions: vi.fn(),
    createFocusSession: vi.fn(),
  },
}));

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  window.sessionStorage.clear();
  vi.clearAllMocks();
});

const subject: AcademicSubject = {
  id: 5,
  semesterId: 2,
  name: "Data Structures",
  code: "CS201",
  credits: 4,
  maximumMarks: 100,
  obtainedMarks: 90,
  percentage: 90,
  officialGrade: "A+",
  gradePoint: 9,
  createdAt: "2026-09-01T10:00:00",
  updatedAt: "2026-09-01T10:00:00",
};

const goal: Goal = {
  id: 12,
  title: "Master algorithms",
  description: "Practice algorithm design.",
  category: "STUDY",
  targetDate: null,
  targetDurationMinutes: 900,
  preferredWeekdays: [],
  status: "IN_PROGRESS",
  createdAt: "2026-09-01T10:00:00",
  updatedAt: "2026-09-01T10:00:00",
};

const academicSummary: AcademicYearSummary = {
  academicYear: {
    id: 1,
    label: "2026-2027",
    startDate: null,
    endDate: null,
    createdAt: "2026-09-01T10:00:00",
    updatedAt: "2026-09-01T10:00:00",
  },
  totalSubjects: 1,
  eligibleSubjects: 1,
  eligibleCredits: 4,
  weightedGradePoints: 36,
  cgpa: 9,
  semesters: [{
    semester: {
      id: 2,
      academicYearId: 1,
      label: "Semester 1",
      startDate: null,
      endDate: null,
      createdAt: "2026-09-01T10:00:00",
      updatedAt: "2026-09-01T10:00:00",
    },
    totalSubjects: 1,
    eligibleSubjects: 1,
    eligibleCredits: 4,
    weightedGradePoints: 36,
    gpa: 9,
    subjects: [subject],
  }],
};

function makeSession(id: number, completedAt: string, durationMinutes = 25): FocusSession {
  return {
    id,
    subjectName: "Data Structures",
    subjectCode: "CS201",
    progressGoalId: 12,
    goalTitle: "Master algorithms",
    durationMinutes,
    startedAt: "2026-11-02T18:00:00.000Z",
    completedAt,
    createdAt: completedAt,
  };
}

function configureApi() {
  vi.mocked(api.initializeSampleData).mockResolvedValue();
  vi.mocked(api.listAcademicYears).mockResolvedValue([academicSummary.academicYear]);
  vi.mocked(api.getAcademicYearSummary).mockResolvedValue(academicSummary);
  vi.mocked(api.listGoals).mockResolvedValue([goal]);
  vi.mocked(api.listFocusSessions).mockResolvedValue([]);
  vi.mocked(api.createFocusSession).mockImplementation(async (request) => makeSession(
    33,
    request.completedAt,
    request.durationMinutes,
  ));
}

describe("Focus Arena", () => {
  it("restores an interrupted timer paused and saves only after the remaining focus time completes", async () => {
    configureApi();
    window.sessionStorage.setItem("focus-arena-timer-v1", JSON.stringify({
      mode: "FOCUS",
      remainingSeconds: 1,
      startedAt: "2026-11-02T18:00:00.000Z",
      requestKey: "63d3ec54-1f30-4d3e-83af-202611021915",
      subjectId: 5,
      subjectName: "Data Structures",
      goalId: 12,
      goalTitle: "Master algorithms",
      pendingRequest: null,
    }));

    render(<MemoryRouter><FocusArenaPage /></MemoryRouter>);
    await waitFor(() => expect(api.listFocusSessions).toHaveBeenCalledOnce());
    expect(screen.getByRole("button", { name: /Resume focus/ })).toBeEnabled();
    expect(api.createFocusSession).not.toHaveBeenCalled();

    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-02T18:26:00.000Z"));
    fireEvent.click(screen.getByRole("button", { name: /Resume focus/ }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_100);
    });

    expect(api.createFocusSession).toHaveBeenCalledOnce();
    expect(api.createFocusSession).toHaveBeenCalledWith(expect.objectContaining({
      subjectId: 5,
      goalId: 12,
      durationMinutes: 25,
      requestKey: "63d3ec54-1f30-4d3e-83af-202611021915",
      startedAt: "2026-11-02T18:00:00.000Z",
    }));
    expect(screen.getByText("Your 25-minute Data Structures session was saved.")).toBeInTheDocument();
    expect(screen.getByRole("timer")).toHaveAccessibleName(/Short break/);
  });

  it("does not submit or count an unfinished session when reset", async () => {
    configureApi();
    render(<MemoryRouter><FocusArenaPage /></MemoryRouter>);

    fireEvent.click(await screen.findByRole("button", { name: /Start focusing/ }));
    fireEvent.click(screen.getByRole("button", { name: "Reset timer" }));

    expect(api.createFocusSession).not.toHaveBeenCalled();
    expect(screen.getByRole("timer")).toHaveAccessibleName(/25:00 remaining/);
  });

  it("offers five-minute focus increments and formats selected hours clearly", async () => {
    configureApi();
    render(<MemoryRouter><FocusArenaPage /></MemoryRouter>);

    const durationSelect = await screen.findByRole("combobox", { name: "Focus duration" });
    expect(durationSelect.querySelectorAll("option")).toHaveLength(24);
    expect(screen.getByRole("option", { name: "1 hour" })).toHaveValue("60");
    expect(screen.getByRole("option", { name: "2 hours" })).toHaveValue("120");
    expect(screen.getByRole("timer")).toHaveAccessibleName(/25:00 remaining/);

    fireEvent.change(durationSelect, { target: { value: "60" } });
    expect(screen.getByText("1 hour selected")).toBeInTheDocument();
    expect(screen.getByRole("timer")).toHaveAccessibleName(/01:00:00 remaining/);

    fireEvent.change(durationSelect, { target: { value: "90" } });
    expect(screen.getByRole("timer")).toHaveAccessibleName(/01:30:00 remaining/);

    fireEvent.change(durationSelect, { target: { value: "120" } });
    expect(screen.getByRole("timer")).toHaveAccessibleName(/02:00:00 remaining/);

    fireEvent.change(durationSelect, { target: { value: "5" } });
    expect(screen.getByRole("timer")).toHaveAccessibleName(/05:00 remaining/);

    fireEvent.change(durationSelect, { target: { value: "60" } });
    fireEvent.click(screen.getByRole("button", { name: "Short break" }));
    expect(screen.getByRole("timer")).toHaveAccessibleName(/Short break, 05:00 remaining/);
    fireEvent.click(screen.getByRole("button", { name: "Long break" }));
    expect(screen.getByRole("timer")).toHaveAccessibleName(/Long break, 15:00 remaining/);
    fireEvent.click(screen.getByRole("button", { name: "Focus session" }));
    expect(screen.getByRole("timer")).toHaveAccessibleName(/01:00:00 remaining/);
  });

  it("restores a one-hour focus round and submits its selected duration", async () => {
    configureApi();
    window.sessionStorage.setItem("focus-arena-timer-v1", JSON.stringify({
      mode: "FOCUS",
      focusDurationMinutes: 60,
      remainingSeconds: 1,
      startedAt: "2026-11-02T18:00:00.000Z",
      requestKey: "63d3ec54-1f30-4d3e-83af-202611021915",
      subjectId: 5,
      subjectName: "Data Structures",
      goalId: 12,
      goalTitle: "Master algorithms",
      pendingRequest: null,
    }));

    render(<MemoryRouter><FocusArenaPage /></MemoryRouter>);
    await waitFor(() => expect(api.listFocusSessions).toHaveBeenCalledOnce());
    expect(screen.getByRole("timer")).toHaveAccessibleName(/00:00:01 remaining/);

    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-02T19:00:00.000Z"));
    fireEvent.click(screen.getByRole("button", { name: /Resume focus/ }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_100);
    });

    expect(api.createFocusSession).toHaveBeenCalledWith(expect.objectContaining({
      durationMinutes: 60,
      startedAt: "2026-11-02T18:00:00.000Z",
    }));
    expect(screen.getByText("Your 60-minute Data Structures session was saved.")).toBeInTheDocument();
  });

  it("shows a clear route to academic progress when there are no subjects", async () => {
    configureApi();
    vi.mocked(api.getAcademicYearSummary).mockResolvedValue({
      ...academicSummary,
      totalSubjects: 0,
      eligibleSubjects: 0,
      eligibleCredits: 0,
      weightedGradePoints: 0,
      cgpa: null,
      semesters: [],
    });

    render(<MemoryRouter><FocusArenaPage /></MemoryRouter>);

    expect(await screen.findByText("Add a subject in Academic progress to begin tracking focus sessions.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open academic progress/ })).toHaveAttribute("href", "/academic");
    expect(screen.getByRole("button", { name: /Start focusing/ })).toBeDisabled();
  });
});
