import { useEffect, useMemo, useState } from "react";
import { BookOpenCheck, GraduationCap, Pencil, Plus, Trash2 } from "lucide-react";
import { api } from "../api/client";
import { AcademicSubjectForm, AcademicYearForm, SemesterForm } from "../components/AcademicForms";
import { Button, EmptyState, ErrorState, formatDate, LoadingState, PageHeader } from "../components/ui";
import type {
  AcademicSubject,
  AcademicSubjectRequest,
  AcademicSummary,
  AcademicYear,
  AcademicYearRequest,
  AcademicYearSummary,
  Semester,
  SemesterRequest,
  SemesterSummary,
} from "../types";

type YearEditor = { kind: "year"; value?: AcademicYear };
type SemesterEditor = { kind: "semester"; value?: Semester };
type SubjectEditor = { kind: "subject"; value?: AcademicSubject };
type Editor = YearEditor | SemesterEditor | SubjectEditor | null;

const gradePoints = [
  ["O", "10"], ["A+", "9"], ["A", "8"], ["B+", "7"], ["B", "6"], ["C", "5"], ["U", "0"],
];

export function AcademicProgressPage() {
  const [years, setYears] = useState<AcademicYear[] | null>(null);
  const [overall, setOverall] = useState<AcademicSummary | null>(null);
  const [yearSummary, setYearSummary] = useState<AcademicYearSummary | null>(null);
  const [semesterSummary, setSemesterSummary] = useState<SemesterSummary | null>(null);
  const [selectedYearId, setSelectedYearId] = useState<number | null>(null);
  const [selectedSemesterId, setSelectedSemesterId] = useState<number | null>(null);
  const [editor, setEditor] = useState<Editor>(null);
  const [error, setError] = useState<unknown>(null);
  const [summaryError, setSummaryError] = useState<unknown>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);

  const selectedYear = useMemo(
    () => years?.find((year) => year.id === selectedYearId) || null,
    [years, selectedYearId],
  );
  const selectedSemester = useMemo(
    () => yearSummary?.semesters.find((item) => item.semester.id === selectedSemesterId) || null,
    [yearSummary, selectedSemesterId],
  );

  const loadOverview = async () => {
    setError(null);
    try {
      const [academicYears, academicSummary] = await Promise.all([
        api.listAcademicYears(),
        api.getAcademicSummary(),
      ]);
      setYears(academicYears);
      setOverall(academicSummary);
      setSelectedYearId((current) =>
        current && academicYears.some((year) => year.id === current)
          ? current : academicYears[0]?.id ?? null);
      if (academicYears.length === 0) {
        setYearSummary(null);
        setSemesterSummary(null);
        setSelectedSemesterId(null);
      }
    } catch (requestError) {
      setError(requestError);
    }
  };

  useEffect(() => { void loadOverview(); }, []);

  useEffect(() => {
    if (selectedYearId === null) {
      setYearSummary(null);
      setSelectedSemesterId(null);
      return;
    }
    let active = true;
    setSummaryLoading(true);
    setSummaryError(null);
    void api.getAcademicYearSummary(selectedYearId)
      .then((summary) => {
        if (!active) return;
        setYearSummary(summary);
        setSelectedSemesterId((current) =>
          current && summary.semesters.some((item) => item.semester.id === current)
            ? current : summary.semesters[0]?.semester.id ?? null);
      })
      .catch((requestError: unknown) => {
        if (active) setSummaryError(requestError);
      })
      .finally(() => {
        if (active) setSummaryLoading(false);
      });
    return () => { active = false; };
  }, [selectedYearId]);

  useEffect(() => {
    if (selectedSemesterId === null) {
      setSemesterSummary(null);
      return;
    }
    let active = true;
    setSemesterSummary(null);
    void api.getSemesterSummary(selectedSemesterId)
      .then((summary) => { if (active) setSemesterSummary(summary); })
      .catch((requestError: unknown) => { if (active) setSummaryError(requestError); });
    return () => { active = false; };
  }, [selectedSemesterId]);

  const refreshSummaries = async (yearId: number, semesterId: number | null) => {
    setSummaryError(null);
    const [yearData, overallData] = await Promise.all([
      api.getAcademicYearSummary(yearId),
      api.getAcademicSummary(),
    ]);
    setYearSummary(yearData);
    setOverall(overallData);
    setError(null);
    const nextSemesterId = semesterId && yearData.semesters.some((item) => item.semester.id === semesterId)
      ? semesterId : yearData.semesters[0]?.semester.id ?? null;
    setSelectedSemesterId(nextSemesterId);
    setSemesterSummary(nextSemesterId
      ? await api.getSemesterSummary(nextSemesterId)
      : null);
  };

  const saveYear = async (request: AcademicYearRequest) => {
    const saved = editor?.kind === "year" && editor.value
      ? await api.updateAcademicYear(editor.value.id, request)
      : await api.createAcademicYear(request);
    const [academicYears, academicSummary] = await Promise.all([
      api.listAcademicYears(),
      api.getAcademicSummary(),
    ]);
    setYears(academicYears);
    setOverall(academicSummary);
    setSelectedYearId(saved.id);
    if (selectedYearId === saved.id) {
      await refreshSummaries(saved.id, selectedSemesterId);
    }
    setEditor(null);
    setError(null);
  };

  const saveSemester = async (request: SemesterRequest) => {
    if (selectedYearId === null) return;
    const saved = editor?.kind === "semester" && editor.value
      ? await api.updateSemester(editor.value.id, request)
      : await api.createSemester(selectedYearId, request);
    await refreshSummaries(selectedYearId, saved.id);
    setEditor(null);
  };

  const saveSubject = async (request: AcademicSubjectRequest) => {
    if (selectedSemesterId === null || selectedYearId === null) return;
    if (editor?.kind === "subject" && editor.value) {
      await api.updateAcademicSubject(editor.value.id, request);
    } else {
      await api.createAcademicSubject(selectedSemesterId, request);
    }
    await refreshSummaries(selectedYearId, selectedSemesterId);
    setEditor(null);
  };

  const deleteYear = async (year: AcademicYear) => {
    if (!window.confirm(`Delete academic year “${year.label}”? Years with semesters cannot be deleted.`)) return;
    try {
      await api.deleteAcademicYear(year.id);
      const [academicYears, academicSummary] = await Promise.all([
        api.listAcademicYears(),
        api.getAcademicSummary(),
      ]);
      setYears(academicYears);
      setOverall(academicSummary);
      setSelectedYearId((current) =>
        current && academicYears.some((item) => item.id === current)
          ? current : academicYears[0]?.id ?? null);
      setEditor(null);
      setError(null);
    } catch (requestError) {
      setError(requestError);
    }
  };

  const deleteSemester = async (semester: Semester) => {
    if (!window.confirm(`Delete ${semester.label}? Remove its subjects first.`)) return;
    if (selectedYearId === null) return;
    try {
      await api.deleteSemester(semester.id);
      await refreshSummaries(selectedYearId, null);
      setEditor(null);
    } catch (requestError) {
      setError(requestError);
    }
  };

  const deleteSubject = async (subject: AcademicSubject) => {
    if (!window.confirm(`Delete “${subject.name}”? This cannot be undone.`)) return;
    if (selectedYearId === null || selectedSemesterId === null) return;
    try {
      await api.deleteAcademicSubject(subject.id);
      await refreshSummaries(selectedYearId, selectedSemesterId);
      setEditor(null);
    } catch (requestError) {
      setError(requestError);
    }
  };

  if (years === null) {
    return error
      ? <><PageHeader eyebrow="YOUR ACADEMIC RECORD" title="Academic progress" /><ErrorState error={error} onRetry={() => void loadOverview()} /></>
      : <LoadingState label="Loading your academic progress…" />;
  }

  return (
    <>
      <PageHeader
        eyebrow="YOUR ACADEMIC RECORD"
        title="Academic progress"
        description="Organize academic years, semesters, and results using your recorded grades."
        action={<Button onClick={() => setEditor({ kind: "year" })}><Plus size={16} /> Add academic year</Button>}
      />
      {error && <ErrorState error={error} onRetry={() => void loadOverview()} />}
      <section className="academic-grading panel" aria-labelledby="grading-heading">
        <div className="panel-heading">
          <div>
            <h2 id="grading-heading">R-2023 CBCCS grading scheme</h2>
            <p>Official letter grades map to grade points as follows.</p>
          </div>
          <span className="academic-scheme-badge">Official mapping</span>
        </div>
        <div className="grade-point-list">
          {gradePoints.map(([grade, point]) => <span key={grade}><strong>{grade}</strong><small>{point} points</small></span>)}
        </div>
        <p className="academic-note">Marks-to-grade boundaries were not provided. Grades are recorded directly; percentages do not assign grades.</p>
      </section>

      {overall && (
        <section className="academic-summary-grid" aria-label="Cumulative academic summary">
          <AcademicMetric label="All subjects" value={overall.totalSubjects} />
          <AcademicMetric label="Eligible subjects" value={overall.eligibleSubjects} />
          <AcademicMetric label="Eligible credits" value={overall.eligibleCredits} />
          <AcademicMetric label="Cumulative CGPA" value={overall.cgpa?.toFixed(2) ?? "Unavailable"} />
        </section>
      )}

      {editor?.kind === "year" && (
        <section className="panel academic-editor">
          <div className="panel-heading"><div><h2>{editor.value ? "Edit academic year" : "New academic year"}</h2></div></div>
          <AcademicYearForm key={editor.value?.id ?? "new"} initial={editor.value} onSubmit={saveYear} onCancel={() => setEditor(null)} />
        </section>
      )}

      {years.length === 0 ? (
        <section className="panel">
          <EmptyState
            icon={<GraduationCap size={24} />}
            title="Start your academic record"
            description="Add an academic year to organize semesters and record your own subjects and results."
            action={<Button onClick={() => setEditor({ kind: "year" })}><Plus size={16} /> Create academic year</Button>}
          />
        </section>
      ) : (
        <>
          <section className="panel academic-section">
            <div className="panel-heading">
              <div><h2>Academic years</h2><p>Select a year to manage its semesters and subjects.</p></div>
            </div>
            <div className="academic-year-list">
              {years.map((year) => (
                <article className={`academic-year-item ${year.id === selectedYearId ? "academic-item-selected" : ""}`} key={year.id}>
                  <button className="academic-item-select" aria-pressed={year.id === selectedYearId} onClick={() => { setSelectedYearId(year.id); setEditor(null); }}>
                    <strong>{year.label}</strong>
                    <small>{year.startDate ? formatDate(year.startDate) : "Dates optional"}{year.endDate ? ` – ${formatDate(year.endDate)}` : ""}</small>
                  </button>
                  <div className="academic-item-actions">
                    <Button variant="ghost" aria-label={`Edit ${year.label}`} onClick={() => setEditor({ kind: "year", value: year })}><Pencil size={15} /></Button>
                    <Button variant="ghost" aria-label={`Delete ${year.label}`} onClick={() => void deleteYear(year)}><Trash2 size={15} /></Button>
                  </div>
                </article>
              ))}
            </div>
          </section>

          {selectedYear && (
            <section className="panel academic-section">
              <div className="panel-heading">
                <div><h2>{selectedYear.label} semesters</h2><p>Year CGPA: {yearSummary?.cgpa?.toFixed(2) ?? "Unavailable"}</p></div>
                <Button variant="secondary" onClick={() => setEditor({ kind: "semester" })}><Plus size={15} /> Add semester</Button>
              </div>
              {editor?.kind === "semester" && (
                <div className="academic-editor-inline">
                  <h3>{editor.value ? "Edit semester" : "New semester"}</h3>
                  <SemesterForm key={editor.value?.id ?? "new"} initial={editor.value} onSubmit={saveSemester} onCancel={() => setEditor(null)} />
                </div>
              )}
              {summaryLoading ? <LoadingState label="Loading semesters…" /> : summaryError
                ? <ErrorState error={summaryError} onRetry={() => void refreshSummaries(selectedYear.id, selectedSemesterId)} />
                : (yearSummary?.semesters.length ?? 0) === 0
                  ? <EmptyState icon={<BookOpenCheck size={22} />} title="No semesters yet" description="Add a semester to record your subjects and results." />
                  : (
                    <>
                      <div className="academic-semester-list" role="group" aria-label="Semesters">
                        {yearSummary?.semesters.map(({ semester, gpa, totalSubjects }) => (
                          <button
                            key={semester.id}
                            aria-pressed={semester.id === selectedSemesterId}
                            className={`academic-semester-tab ${semester.id === selectedSemesterId ? "academic-item-selected" : ""}`}
                            onClick={() => { setSelectedSemesterId(semester.id); setEditor(null); }}
                          >
                            <strong>{semester.label}</strong><small>{totalSubjects} subjects · GPA {gpa?.toFixed(2) ?? "—"}</small>
                          </button>
                        ))}
                      </div>
                      {selectedSemester && (
                        <>
                          <div className="academic-semester-actions">
                            <span>{formatDate(selectedSemester.semester.startDate)}{selectedSemester.semester.endDate ? ` – ${formatDate(selectedSemester.semester.endDate)}` : ""}</span>
                            <div>
                              <Button variant="ghost" aria-label={`Edit ${selectedSemester.semester.label}`} onClick={() => setEditor({ kind: "semester", value: selectedSemester.semester })}><Pencil size={15} /> Edit semester</Button>
                              <Button variant="ghost" aria-label={`Delete ${selectedSemester.semester.label}`} onClick={() => void deleteSemester(selectedSemester.semester)}><Trash2 size={15} /> Delete semester</Button>
                            </div>
                          </div>
                          <div className="academic-selected-summary">
                            <AcademicMetric label="Semester subjects" value={selectedSemester.totalSubjects} />
                            <AcademicMetric label="Eligible credits" value={selectedSemester.eligibleCredits} />
                            <AcademicMetric label="Semester GPA" value={semesterSummary?.gpa?.toFixed(2) ?? "Unavailable"} />
                          </div>
                          <div className="academic-subject-heading">
                            <div><h3>Subjects</h3><p>Grades and grade points are recorded separately from marks and percentages.</p></div>
                            <Button onClick={() => setEditor({ kind: "subject" })}><Plus size={15} /> Add subject</Button>
                          </div>
                          {editor?.kind === "subject" && (
                            <div className="academic-editor-inline">
                              <h3>{editor.value ? "Edit subject" : "New subject"}</h3>
                              <AcademicSubjectForm key={editor.value?.id ?? "new"} initial={editor.value} onSubmit={saveSubject} onCancel={() => setEditor(null)} />
                            </div>
                          )}
                          {summaryError
                            ? <ErrorState error={summaryError} onRetry={() => void refreshSummaries(selectedYear.id, selectedSemesterId)} />
                            : semesterSummary ? semesterSummary.subjects.length === 0
                            ? <EmptyState icon={<BookOpenCheck size={22} />} title="No subjects recorded" description="Add a subject and enter marks or an official grade when available." />
                            : <SubjectTable subjects={semesterSummary.subjects} onEdit={(subject) => setEditor({ kind: "subject", value: subject })} onDelete={(subject) => void deleteSubject(subject)} />
                            : <LoadingState label="Loading subject summary…" />}
                        </>
                      )}
                    </>
                  )}
            </section>
          )}
        </>
      )}

      <section className="panel academic-formula">
        <h2>GPA and CGPA calculation</h2>
        <p>Σ(credits × grade points) ÷ Σ(eligible credits), rounded to two decimal places using half-up rounding.</p>
        <p>Only successfully cleared graded subjects count. U grades and subjects without an official grade are excluded; their credits are not included. Marks and percentages do not determine grade points.</p>
      </section>
    </>
  );
}

function AcademicMetric({ label, value }: { label: string; value: number | string }) {
  return <article className="academic-metric"><span>{label}</span><strong>{value}</strong></article>;
}

function SubjectTable({
  subjects,
  onEdit,
  onDelete,
}: {
  subjects: AcademicSubject[];
  onEdit: (subject: AcademicSubject) => void;
  onDelete: (subject: AcademicSubject) => void;
}) {
  return (
    <div className="academic-table-wrap">
      <table className="academic-table">
        <caption className="sr-only">Recorded subjects, marks, percentages, and official grades</caption>
        <thead><tr><th scope="col">Subject</th><th scope="col">Credits</th><th scope="col">Marks</th><th scope="col">Percentage</th><th scope="col">Official grade</th><th scope="col">Grade points</th><th scope="col"><span className="sr-only">Actions</span></th></tr></thead>
        <tbody>
          {subjects.map((subject) => (
            <tr key={subject.id}>
              <th scope="row"><strong>{subject.name}</strong><small>{subject.code || "No code"}</small></th>
              <td>{subject.credits}</td>
              <td>{subject.obtainedMarks == null ? "—" : `${subject.obtainedMarks} / ${subject.maximumMarks}`}</td>
              <td>{subject.percentage == null ? "—" : `${subject.percentage.toFixed(2)}%`}</td>
              <td>{subject.officialGrade ?? "Not recorded"}</td>
              <td>{subject.gradePoint ?? "—"}</td>
              <td><div className="academic-table-actions">
                <Button variant="ghost" aria-label={`Edit ${subject.name}`} onClick={() => onEdit(subject)}><Pencil size={15} /></Button>
                <Button variant="ghost" aria-label={`Delete ${subject.name}`} onClick={() => onDelete(subject)}><Trash2 size={15} /></Button>
              </div></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
