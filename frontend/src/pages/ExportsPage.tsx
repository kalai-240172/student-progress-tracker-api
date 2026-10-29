import { useEffect, useState } from "react";
import { Download, FileSpreadsheet } from "lucide-react";
import { api } from "../api/client";
import { Button, ErrorState, LoadingState, PageHeader } from "../components/ui";
import type { AcademicYear, Semester } from "../types";

type ExportKind = "study-history" | "tasks" | "academic";
type DateRange = { startDate: string; endDate: string };

function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.style.display = "none";
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function validDateRange(range: DateRange): string | null {
  if (!range.startDate && !range.endDate) return null;
  if (!range.startDate || !range.endDate) return "Choose both a start date and an end date.";
  if (range.startDate > range.endDate) return "Start date must be on or before end date.";
  return null;
}

export function ExportsPage() {
  const [range, setRange] = useState<DateRange>({ startDate: "", endDate: "" });
  const [academicYears, setAcademicYears] = useState<AcademicYear[] | null>(null);
  const [selectedYearId, setSelectedYearId] = useState("");
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [semesterId, setSemesterId] = useState("");
  const [loading, setLoading] = useState<ExportKind | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [validationError, setValidationError] = useState("");
  const [success, setSuccess] = useState("");
  const [failedExport, setFailedExport] = useState<ExportKind | null>(null);

  const loadYears = () => {
    setError(null);
    setAcademicYears(null);
    void api.listAcademicYears()
      .then(setAcademicYears)
      .catch(setError);
  };

  useEffect(loadYears, []);

  useEffect(() => {
    setSemesterId("");
    setError(null);
    setFailedExport(null);
    if (!selectedYearId) {
      setSemesters([]);
      return;
    }
    let active = true;
    void api.listSemesters(Number(selectedYearId))
      .then((items) => { if (active) setSemesters(items); })
      .catch((requestError: unknown) => { if (active) setError(requestError); });
    return () => { active = false; };
  }, [selectedYearId]);

  const download = async (kind: ExportKind) => {
    setValidationError("");
    setError(null);
    setSuccess("");
    setFailedExport(null);
    if (kind !== "academic") {
      const invalid = validDateRange(range);
      if (invalid) {
        setValidationError(invalid);
        return;
      }
    }
    setLoading(kind);
    try {
      const filters = {
        ...(range.startDate ? { startDate: range.startDate } : {}),
        ...(range.endDate ? { endDate: range.endDate } : {}),
      };
      const result = kind === "study-history"
        ? await api.exportStudyHistory(filters)
        : kind === "tasks"
          ? await api.exportTasks(filters)
          : await api.exportAcademic({
            ...(selectedYearId ? { academicYearId: Number(selectedYearId) } : {}),
            ...(semesterId ? { semesterId: Number(semesterId) } : {}),
          });
      triggerDownload(result.blob, result.filename);
      setSuccess(`Downloaded ${result.filename}. If no records match, the CSV contains its headers only.`);
    } catch (requestError) {
      setFailedExport(kind);
      setError(requestError);
    } finally {
      setLoading(null);
    }
  };

  const retry = () => {
    if (failedExport) {
      void download(failedExport);
    } else if (academicYears === null) {
      loadYears();
    } else if (selectedYearId) {
      void api.listSemesters(Number(selectedYearId))
        .then(setSemesters)
        .catch(setError);
    } else {
      setError(null);
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="YOUR DATA, YOUR COPY"
        title="Export data"
        description="Download your saved study and academic data as CSV files."
      />
      {error && <ErrorState error={error} onRetry={retry} />}
      {validationError && <p className="field-error export-validation" role="alert">{validationError}</p>}
      {success && <p className="export-success" role="status">{success}</p>}

      <section className="export-grid" aria-label="CSV export options">
        <ExportCard
          title="Study history"
          description="Export your saved progress logs, including goal, activity date, recorded minutes, summary, and notes."
          loading={loading === "study-history"}
          disabled={loading !== null}
          onDownload={() => void download("study-history")}
        />
        <ExportCard
          title="Daily tasks"
          description="Export tasks with planned dates, status, estimated time, and actual time only when recorded."
          loading={loading === "tasks"}
          disabled={loading !== null}
          onDownload={() => void download("tasks")}
        />
        <article className="panel export-card">
          <div className="export-card-icon"><FileSpreadsheet size={20} /></div>
          <h2>Academic results</h2>
          <p>Export subjects, marks, percentages, official grades, grade points, and available GPA/CGPA summaries.</p>
          {academicYears === null ? <LoadingState label="Loading academic years…" /> : (
            <div className="export-filters">
              <label className="field">
                <span>Academic year</span>
                <select aria-label="Academic year filter" value={selectedYearId} onChange={(event) => setSelectedYearId(event.target.value)}>
                  <option value="">All academic years</option>
                  {academicYears.map((year) => <option key={year.id} value={year.id}>{year.label}</option>)}
                </select>
              </label>
              <label className="field">
                <span>Semester</span>
                <select aria-label="Semester filter" value={semesterId} disabled={!selectedYearId} onChange={(event) => setSemesterId(event.target.value)}>
                  <option value="">All semesters</option>
                  {semesters.map((semester) => <option key={semester.id} value={semester.id}>{semester.label}</option>)}
                </select>
              </label>
            </div>
          )}
          <Button onClick={() => void download("academic")} disabled={loading !== null || academicYears === null}>
            <Download size={15} /> {loading === "academic" ? "Preparing CSV…" : "Download academic CSV"}
          </Button>
        </article>
      </section>

      <section className="panel export-date-panel">
        <div className="panel-heading">
          <div><h2>Study-history and task date range</h2><p>Both dates are optional; when used, the range is inclusive in the server's local calendar date.</p></div>
        </div>
        <div className="export-date-filters">
          <label className="field">
            <span>Start date</span>
            <input aria-label="Export start date" type="date" value={range.startDate} onChange={(event) => setRange((current) => ({ ...current, startDate: event.target.value }))} />
          </label>
          <label className="field">
            <span>End date</span>
            <input aria-label="Export end date" type="date" value={range.endDate} onChange={(event) => setRange((current) => ({ ...current, endDate: event.target.value }))} />
          </label>
          <Button variant="secondary" onClick={() => setRange({ startDate: "", endDate: "" })}>Clear dates</Button>
        </div>
      </section>

      <p className="export-security-note">CSV files use UTF-8 encoding and escape quotes, commas, and line breaks. Text that could be interpreted as a spreadsheet formula is prefixed safely. Exports include only your authenticated account's data.</p>
    </>
  );
}

function ExportCard({
  title,
  description,
  loading,
  disabled,
  onDownload,
}: {
  title: string;
  description: string;
  loading: boolean;
  disabled: boolean;
  onDownload: () => void;
}) {
  return (
    <article className="panel export-card">
      <div className="export-card-icon"><FileSpreadsheet size={20} /></div>
      <h2>{title}</h2>
      <p>{description}</p>
      <Button onClick={onDownload} disabled={disabled}>
        <Download size={15} /> {loading ? "Preparing CSV…" : `Download ${title.toLowerCase()} CSV`}
      </Button>
    </article>
  );
}
