import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api/client";
import type { AcademicSummary, AcademicYear, AcademicYearSummary } from "../types";
import { AcademicProgressPage } from "./AcademicProgressPage";

vi.mock("../api/client", () => ({
  api: {
    listAcademicYears: vi.fn(),
    getAcademicSummary: vi.fn(),
    getAcademicYearSummary: vi.fn(),
    getSemesterSummary: vi.fn(),
    createAcademicYear: vi.fn(),
    updateAcademicYear: vi.fn(),
    deleteAcademicYear: vi.fn(),
    createSemester: vi.fn(),
    updateSemester: vi.fn(),
    deleteSemester: vi.fn(),
    createAcademicSubject: vi.fn(),
    updateAcademicSubject: vi.fn(),
    deleteAcademicSubject: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const emptySummary: AcademicSummary = {
  totalSubjects: 0,
  eligibleSubjects: 0,
  eligibleCredits: 0,
  weightedGradePoints: 0,
  cgpa: null,
  academicYears: [],
};

const year: AcademicYear = {
  id: 1,
  label: "2026-2027",
  startDate: null,
  endDate: null,
  createdAt: "2026-01-01T00:00:00",
  updatedAt: "2026-01-01T00:00:00",
};

const yearSummary: AcademicYearSummary = {
  academicYear: year,
  totalSubjects: 1,
  eligibleSubjects: 1,
  eligibleCredits: 4,
  weightedGradePoints: 40,
  cgpa: 10,
  semesters: [{
    semester: {
      id: 11,
      academicYearId: 1,
      label: "Semester 1",
      startDate: null,
      endDate: null,
      createdAt: "2026-01-01T00:00:00",
      updatedAt: "2026-01-01T00:00:00",
    },
    totalSubjects: 1,
    eligibleSubjects: 1,
    eligibleCredits: 4,
    weightedGradePoints: 40,
    gpa: 10,
    subjects: [{
      id: 21,
      semesterId: 11,
      name: "Programming",
      code: "CS101",
      credits: 4,
      maximumMarks: 100,
      obtainedMarks: 95,
      percentage: 95,
      officialGrade: "O",
      gradePoint: 10,
      createdAt: "2026-01-01T00:00:00",
      updatedAt: "2026-01-01T00:00:00",
    }],
  }],
};

describe("academic progress page", () => {
  beforeEach(() => {
    vi.mocked(api.listAcademicYears).mockResolvedValue([]);
    vi.mocked(api.getAcademicSummary).mockResolvedValue(emptySummary);
    vi.mocked(api.getAcademicYearSummary).mockResolvedValue(yearSummary);
    vi.mocked(api.getSemesterSummary).mockResolvedValue(yearSummary.semesters[0]);
  });

  it("shows loading and a useful empty academic-year state", async () => {
    let resolveYears: ((years: AcademicYear[]) => void) | undefined;
    vi.mocked(api.listAcademicYears).mockImplementationOnce(() => new Promise((resolve) => {
      resolveYears = resolve;
    }));
    render(<AcademicProgressPage />);
    expect(screen.getByRole("status")).toHaveTextContent("Loading your academic progress");
    resolveYears?.([]);
    expect(await screen.findByText("Start your academic record")).toBeInTheDocument();
    expect(screen.getByText("R-2023 CBCCS grading scheme")).toBeInTheDocument();
  });

  it("creates and selects an academic year, then displays its real summary", async () => {
    vi.mocked(api.createAcademicYear).mockResolvedValue(year);
    vi.mocked(api.listAcademicYears)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([year]);
    vi.mocked(api.getAcademicSummary)
      .mockResolvedValueOnce(emptySummary)
      .mockResolvedValueOnce({ ...emptySummary, academicYears: [yearSummary] });
    render(<AcademicProgressPage />);
    await screen.findByText("Start your academic record");
    fireEvent.click(screen.getByRole("button", { name: "Create academic year" }));
    fireEvent.change(screen.getByLabelText(/Academic year label/), { target: { value: year.label } });
    fireEvent.click(screen.getByRole("button", { name: "Create year" }));

    expect(await screen.findByText("2026-2027 semesters")).toBeInTheDocument();
    expect(api.createAcademicYear).toHaveBeenCalledWith({
      label: "2026-2027",
      startDate: null,
      endDate: null,
    });
    await waitFor(() => expect(api.getAcademicYearSummary).toHaveBeenCalledWith(1));
  });

  it("renders percentage, official grade, GPA and CGPA without converting marks to grades", async () => {
    vi.mocked(api.listAcademicYears).mockResolvedValue([year]);
    vi.mocked(api.getAcademicSummary).mockResolvedValue({
      ...emptySummary,
      totalSubjects: 1,
      eligibleSubjects: 1,
      eligibleCredits: 4,
      cgpa: 10,
      academicYears: [yearSummary],
    });
    render(<AcademicProgressPage />);

    expect(await screen.findByText("95.00%")).toBeInTheDocument();
    expect(screen.getByText("Cumulative CGPA")).toBeInTheDocument();
    expect(screen.getByText("Semester GPA")).toBeInTheDocument();
    expect(screen.getAllByText("O")).toHaveLength(2);
    expect(screen.getByText(/percentages do not assign grades/i)).toBeInTheDocument();
  });

  it("creates a semester within the selected academic year", async () => {
    vi.mocked(api.listAcademicYears).mockResolvedValue([year]);
    vi.mocked(api.getAcademicSummary).mockResolvedValue({
      ...emptySummary,
      academicYears: [yearSummary],
    });
    vi.mocked(api.createSemester).mockResolvedValue(yearSummary.semesters[0].semester);
    render(<AcademicProgressPage />);
    await screen.findByText("2026-2027 semesters");
    fireEvent.click(screen.getByRole("button", { name: "Add semester" }));
    fireEvent.change(screen.getByLabelText(/Semester label/), { target: { value: "Semester 1" } });
    fireEvent.click(screen.getByRole("button", { name: "Create semester" }));

    await waitFor(() => expect(api.createSemester).toHaveBeenCalledWith(1, {
      label: "Semester 1",
      startDate: null,
      endDate: null,
    }));
    const semesterPicker = screen.getByRole("group", { name: "Semesters" });
    expect(semesterPicker.querySelector("button")).toHaveAttribute("aria-pressed", "true");
  });

  it("validates subject credits and marks, shows the official point, and saves explicit input", async () => {
    vi.mocked(api.listAcademicYears).mockResolvedValue([year]);
    vi.mocked(api.getAcademicSummary).mockResolvedValue({
      ...emptySummary,
      academicYears: [yearSummary],
    });
    vi.mocked(api.createAcademicSubject).mockResolvedValue(yearSummary.semesters[0].subjects[0]);
    render(<AcademicProgressPage />);
    await screen.findByText("Programming");
    fireEvent.click(screen.getAllByRole("button", { name: "Add subject" }).at(-1)!);
    fireEvent.change(screen.getByLabelText(/Subject name/), { target: { value: "New subject" } });
    fireEvent.change(screen.getByLabelText(/Credits/), { target: { value: "0" } });
    fireEvent.change(screen.getByLabelText(/Maximum marks/), { target: { value: "100" } });
    fireEvent.change(screen.getByLabelText(/Obtained marks/), { target: { value: "101" } });
    fireEvent.change(screen.getByLabelText("Official letter grade"), { target: { value: "A+" } });
    expect(screen.getByText("R-2023 CBCCS: A+ = 9 grade points.")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "Add subject" }).at(-1)!);

    expect(await screen.findByText("Credits must be a positive whole number")).toBeInTheDocument();
    expect(screen.getByText("Obtained marks cannot exceed maximum marks")).toBeInTheDocument();
    expect(api.createAcademicSubject).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/Credits/), { target: { value: "4" } });
    fireEvent.change(screen.getByLabelText(/Obtained marks/), { target: { value: "95" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Add subject" }).at(-1)!);
    await waitFor(() => expect(api.createAcademicSubject).toHaveBeenCalledWith(11, {
      name: "New subject",
      code: null,
      credits: 4,
      maximumMarks: 100,
      obtainedMarks: 95,
      officialGrade: "A+",
    }));
  });

  it("asks for confirmation before deleting a subject", async () => {
    vi.mocked(api.listAcademicYears).mockResolvedValue([year]);
    vi.mocked(api.getAcademicSummary).mockResolvedValue({ ...emptySummary, academicYears: [yearSummary] });
    const confirmMock = vi.fn().mockReturnValue(false);
    vi.stubGlobal("confirm", confirmMock);
    render(<AcademicProgressPage />);
    await screen.findByText("Programming");
    fireEvent.click(screen.getByRole("button", { name: "Delete Programming" }));

    expect(confirmMock).toHaveBeenCalledWith('Delete “Programming”? This cannot be undone.');
    expect(api.deleteAcademicSubject).not.toHaveBeenCalled();
  });

  it("shows a clear API error and retry action", async () => {
    vi.mocked(api.listAcademicYears)
      .mockRejectedValueOnce(new Error("Academic service unavailable"))
      .mockResolvedValueOnce([]);
    render(<AcademicProgressPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Academic service unavailable");
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByText("Start your academic record")).toBeInTheDocument();
  });

  it("validates the academic year label before sending a create request", async () => {
    render(<AcademicProgressPage />);
    await screen.findByText("Start your academic record");
    fireEvent.click(screen.getByRole("button", { name: "Create academic year" }));
    fireEvent.click(screen.getByRole("button", { name: "Create year" }));

    expect(await screen.findByText("Academic year is required")).toBeInTheDocument();
    expect(api.createAcademicYear).not.toHaveBeenCalled();
  });
});
