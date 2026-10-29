import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, ArrowRight, BarChart3, CheckCircle2, Clock3, Timer } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api } from "../api/client";
import type { DailyTask } from "../types";
import { Button, EmptyState, ErrorState, formatDate, formatMinutes, LoadingState, PageHeader } from "../components/ui";
import { formatLocalDate } from "../tasks/dateUtils";
import {
  buildReportMetrics,
  defaultCustomRange,
  nextReportAnchor,
  previousReportAnchor,
  reportRange,
  type ReportPeriod,
  type ReportRange,
} from "../reports/reportUtils";

function readableDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  if (hours === 0) return `${remainingMinutes} min`;
  if (remainingMinutes === 0) return `${hours} hr`;
  return `${hours} hr ${remainingMinutes} min`;
}

function currentDate(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

export function ReportsPage() {
  const today = formatLocalDate(currentDate());
  const [period, setPeriod] = useState<ReportPeriod>("week");
  const [anchor, setAnchor] = useState(currentDate);
  const [customRange, setCustomRange] = useState(() => defaultCustomRange(currentDate()));
  const [appliedCustomRange, setAppliedCustomRange] = useState<ReportRange | null>(null);
  const [tasks, setTasks] = useState<DailyTask[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [filterError, setFilterError] = useState("");

  const range = useMemo(
    () => period === "custom" ? appliedCustomRange : reportRange(anchor, period, currentDate()),
    [period, anchor, appliedCustomRange],
  );
  const load = useCallback(() => {
    if (!range) return;
    setError(null);
    setTasks(null);
    void api.listTasks(range).then(setTasks).catch(setError);
  }, [range?.startDate, range?.endDate]);
  useEffect(() => { load(); }, [load]);

  const metrics = useMemo(
    () => tasks && range ? buildReportMetrics(tasks, range, today) : null,
    [tasks, range, today],
  );

  const choosePeriod = (nextPeriod: ReportPeriod) => {
    setFilterError("");
    setPeriod(nextPeriod);
    if (nextPeriod === "custom" && !appliedCustomRange) {
      const initialRange = defaultCustomRange(currentDate());
      setCustomRange(initialRange);
      setAppliedCustomRange(initialRange);
    }
  };

  const navigate = (direction: -1 | 1) => {
    if (period === "custom") return;
    const changed = direction < 0
      ? previousReportAnchor(anchor, period)
      : nextReportAnchor(anchor, period);
    if (changed > currentDate()) return;
    setAnchor(changed);
  };

  const applyCustomRange = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFilterError("");
    if (!customRange.startDate || !customRange.endDate) {
      setFilterError("Choose both a start and end date.");
      return;
    }
    if (customRange.startDate > customRange.endDate) {
      setFilterError("Start date must be on or before end date.");
      return;
    }
    if (customRange.endDate > today) {
      setFilterError("Reports cannot include future dates.");
      return;
    }
    setAppliedCustomRange({ ...customRange });
  };

  const periodLabel = range
    ? period === "week" && range.startDate <= today && range.endDate <= today
      ? `${formatDate(range.startDate)} – ${formatDate(range.endDate)}`
      : `${formatDate(range.startDate)} – ${formatDate(range.endDate)}`
    : "Choose a reporting range";
  const chartData = metrics?.days.map((day) => ({
    date: formatDate(day.date),
    plannedMinutes: day.plannedMinutes,
    actualMinutes: day.actualMinutes,
    completedTasks: day.completedTasks,
    remainingTasks: day.totalTasks - day.completedTasks,
  })) || [];

  if (error && tasks === null) {
    return <><PageHeader eyebrow="YOUR LEARNING, IN NUMBERS" title="Reports" description="Review task completion and study time from your saved tasks." /><ErrorState error={error} onRetry={load} /></>;
  }

  return (
    <>
      <PageHeader eyebrow="YOUR LEARNING, IN NUMBERS" title="Reports" description="Review task completion and study time from your saved tasks." />
      {error && <ErrorState error={error} onRetry={load} />}
      <section className="panel report-filter-panel" aria-label="Reporting period">
        <div className="report-period-toolbar">
          <div className="report-period-buttons" role="group" aria-label="Reporting period type">
            {(["week", "month", "custom"] as const).map((value) => (
              <Button key={value} variant={period === value ? "primary" : "secondary"} aria-pressed={period === value} onClick={() => choosePeriod(value)}>
                {value === "week" ? "Weekly" : value === "month" ? "Monthly" : "Custom"}
              </Button>
            ))}
          </div>
          {period !== "custom" && (
            <div className="report-period-navigation">
              <Button variant="secondary" aria-label="Previous reporting period" onClick={() => navigate(-1)}><ArrowLeft size={16} /></Button>
              <strong aria-live="polite">{period === "week" ? "Week" : "Month"} · {periodLabel}</strong>
              <Button variant="secondary" aria-label="Next reporting period" disabled={nextReportAnchor(anchor, period) > currentDate()} onClick={() => navigate(1)}><ArrowRight size={16} /></Button>
              <Button variant="ghost" onClick={() => setAnchor(currentDate())}>Current period</Button>
            </div>
          )}
        </div>
        {period === "custom" && (
          <form className="report-custom-range" onSubmit={applyCustomRange}>
            <label className="field"><span>Start date</span><input aria-label="Report start date" type="date" max={customRange.endDate || today} value={customRange.startDate} onChange={(event) => setCustomRange((rangeValue) => ({ ...rangeValue, startDate: event.target.value }))} /></label>
            <label className="field"><span>End date</span><input aria-label="Report end date" type="date" min={customRange.startDate || undefined} max={today} value={customRange.endDate} onChange={(event) => setCustomRange((rangeValue) => ({ ...rangeValue, endDate: event.target.value }))} /></label>
            <Button type="submit">Apply range</Button>
          </form>
        )}
        {filterError && <p className="field-error" role="alert">{filterError}</p>}
        {range && <p className="report-range-caption">Inclusive date range: {periodLabel}. Future dates are excluded.</p>}
      </section>

      {range && tasks === null ? <LoadingState label="Calculating your reports…" /> : metrics && (
        metrics.totalTasks === 0 ? (
          <section className="panel">
            <EmptyState icon={<BarChart3 size={23} />} title="No task data for this period" description="Plan tasks and record study time to build meaningful reports. This report never fills in missing data." />
          </section>
        ) : (
          <>
            <section className="report-metrics-grid" aria-label="Task report summary">
              <ReportMetric icon={<CheckCircle2 size={17} />} label="Completed tasks" value={metrics.completedTasks} detail={`${metrics.totalTasks} scheduled tasks`} />
              <ReportMetric icon={<Clock3 size={17} />} label="Actual study time" value={readableDuration(metrics.actualMinutes)} detail={`${metrics.actualMinutes} recorded minutes`} />
              <ReportMetric icon={<BarChart3 size={17} />} label="Task completion rate" value={`${metrics.completionRate.toFixed(1)}%`} detail="Completed ÷ all tasks in this period" />
              <ReportMetric icon={<Timer size={17} />} label="Planned vs actual" value={`${formatMinutes(metrics.plannedMinutes)} / ${formatMinutes(metrics.actualMinutes)}`} detail="Actual totals include recorded durations only" />
            </section>
            <section className="report-charts-grid">
              <article className="panel report-chart-panel">
                <div className="panel-heading"><div><h2>Daily study duration</h2><p>Planned time compared with recorded actual time</p></div></div>
                <div className="report-chart" role="img" aria-label="Daily planned and actual study duration chart">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData} margin={{ top: 8, right: 12, bottom: 8, left: 0 }}>
                      <CartesianGrid stroke="#edf0f5" vertical={false} />
                      <XAxis dataKey="date" tick={{ fill: "#858da0", fontSize: 10 }} />
                      <YAxis allowDecimals={false} tick={{ fill: "#858da0", fontSize: 10 }} />
                      <Tooltip formatter={(value) => [`${value ?? 0} minutes`]} />
                      <Bar dataKey="plannedMinutes" name="Planned minutes" fill="#777be7" radius={[4, 4, 0, 0]} />
                      <Bar dataKey="actualMinutes" name="Recorded actual minutes" fill="#4aab88" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
                <p className="report-chart-note">Tasks without actual duration contribute no actual minutes; they are not estimated.</p>
              </article>
              <article className="panel report-chart-panel">
                <div className="panel-heading"><div><h2>Task completion trend</h2><p>Completed tasks compared with all other scheduled tasks per day</p></div></div>
                <div className="report-chart" role="img" aria-label="Daily completed and not completed task count chart">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData} margin={{ top: 8, right: 12, bottom: 8, left: 0 }}>
                      <CartesianGrid stroke="#edf0f5" vertical={false} />
                      <XAxis dataKey="date" tick={{ fill: "#858da0", fontSize: 10 }} />
                      <YAxis allowDecimals={false} tick={{ fill: "#858da0", fontSize: 10 }} />
                      <Tooltip formatter={(value) => [`${value ?? 0} tasks`]} />
                      <Bar dataKey="completedTasks" name="Completed tasks" stackId="tasks" fill="#4aab88" radius={[4, 4, 0, 0]} />
                      <Bar dataKey="remainingTasks" name="Not completed" stackId="tasks" fill="#cbd2e2" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </article>
            </section>
          </>
        )
      )}
    </>
  );
}

function ReportMetric({
  icon,
  label,
  value,
  detail,
}: {
  icon: React.ReactNode;
  label: string;
  value: string | number;
  detail: string;
}) {
  return (
    <article className="report-metric-card">
      <span className="report-metric-icon">{icon}</span>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}
