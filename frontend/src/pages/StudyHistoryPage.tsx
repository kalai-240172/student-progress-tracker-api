import { useCallback, useEffect, useMemo, useState } from "react";
import { CalendarClock, Clock3, Flame, RotateCcw } from "lucide-react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "../api/client";
import type { DailyTask, StudyStreak } from "../types";
import { Button, EmptyState, ErrorState, formatDate, formatMinutes, LoadingState, PageHeader, StatusBadge } from "../components/ui";
import { formatLocalDate, groupTaskHistory, identifyOverdueTasks } from "../tasks/dateUtils";

function daysAgo(value: string, days: number): string {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() - days);
  return formatLocalDate(date);
}

export function StudyHistoryPage() {
  const today = formatLocalDate(new Date());
  const [startDate, setStartDate] = useState(daysAgo(today, 29));
  const [endDate, setEndDate] = useState(today);
  const [appliedRange, setAppliedRange] = useState({ startDate: daysAgo(today, 29), endDate: today });
  const [tasks, setTasks] = useState<DailyTask[] | null>(null);
  const [overdueTasks, setOverdueTasks] = useState<DailyTask[] | null>(null);
  const [streak, setStreak] = useState<StudyStreak | null>(null);
  const [rescheduleDates, setRescheduleDates] = useState<Record<number, string>>({});
  const [error, setError] = useState<unknown>(null);
  const [rangeError, setRangeError] = useState("");

  const load = useCallback(() => {
    setError(null);
    setTasks(null);
    void Promise.all([
      api.listTasks(appliedRange),
      api.getOverdueTasks(),
      api.getStudyStreaks(),
    ])
      .then(([rangeTasks, overdue, streaks]) => {
        setTasks(rangeTasks);
        setOverdueTasks(overdue);
        setStreak(streaks);
        setRescheduleDates(Object.fromEntries(overdue.map((task) => [task.id, today])));
      })
      .catch(setError);
  }, [appliedRange.startDate, appliedRange.endDate, today]);
  useEffect(() => { load(); }, [load]);

  const days = useMemo(() => groupTaskHistory(tasks || []), [tasks]);
  const rangeStats = useMemo(() => {
    const totalTasks = days.reduce((sum, day) => sum + day.taskCount, 0);
    const completed = days.reduce((sum, day) => sum + day.completedCount, 0);
    return {
      taskCount: totalTasks,
      completed,
      planned: days.reduce((sum, day) => sum + day.plannedMinutes, 0),
      actual: days.reduce((sum, day) => sum + day.actualMinutes, 0),
      completionRate: totalTasks === 0 ? 0 : (completed / totalTasks) * 100,
    };
  }, [days]);
  const overdue = useMemo(() => identifyOverdueTasks(overdueTasks || [], today), [overdueTasks, today]);

  const applyFilter = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setRangeError("");
    if (!startDate || !endDate) {
      setRangeError("Choose both a start date and an end date.");
      return;
    }
    if (startDate > endDate) {
      setRangeError("Start date must be on or before end date.");
      return;
    }
    setAppliedRange({ startDate, endDate });
  };

  const reschedule = async (task: DailyTask) => {
    const plannedDate = rescheduleDates[task.id];
    if (!plannedDate) {
      setError(new Error("Choose a new planned date before rescheduling."));
      return;
    }
    setError(null);
    try {
      await api.updateTask(task.id, {
        title: task.title,
        description: task.description,
        subject: task.subject,
        plannedDate,
        estimatedDurationMinutes: task.estimatedDurationMinutes,
      });
      load();
    } catch (requestError) {
      setError(requestError);
    }
  };

  const chartData = days.slice().reverse().map((day) => ({
    date: formatDate(day.date),
    planned: day.plannedMinutes,
    actual: day.actualMinutes,
  }));

  if (error && (tasks === null || !streak)) {
    return <><PageHeader eyebrow="LOOK BACK, KEEP GOING" title="Study history" description="Review planned work, recorded time, and completed study days." /><ErrorState error={error} onRetry={load} /></>;
  }

  return (
    <>
      <PageHeader eyebrow="LOOK BACK, KEEP GOING" title="Study history" description="Review planned work, recorded time, and completed study days." />
      {error && <ErrorState error={error} onRetry={load} />}
      <section className="panel history-filter-panel">
        <form className="history-filter-form" onSubmit={applyFilter}>
          <label className="field"><span>Start date</span><input type="date" value={startDate} max={endDate || undefined} onChange={(event) => setStartDate(event.target.value)} /></label>
          <label className="field"><span>End date</span><input type="date" value={endDate} min={startDate || undefined} max={today} onChange={(event) => setEndDate(event.target.value)} /></label>
          <Button type="submit">Apply date range</Button>
        </form>
        {rangeError && <p className="field-error" role="alert">{rangeError}</p>}
        <p className="history-range-note">Both selected dates are included. Study streaks are calculated across all completed task dates.</p>
      </section>
      {tasks === null || !streak ? <LoadingState label="Loading your study history…" /> : (
        <>
          <section className="history-metrics" aria-label="History summary">
            <HistoryMetric icon={<CalendarClock size={17} />} label="Tasks in range" value={rangeStats.taskCount} />
            <HistoryMetric icon={<Flame size={17} />} label="Completed tasks" value={rangeStats.completed} />
            <HistoryMetric icon={<Clock3 size={17} />} label="Planned time" value={formatMinutes(rangeStats.planned)} />
            <HistoryMetric icon={<Clock3 size={17} />} label="Recorded actual time" value={formatMinutes(rangeStats.actual)} />
            <HistoryMetric icon={<Flame size={17} />} label="Completion rate" value={`${rangeStats.completionRate.toFixed(0)}%`} />
          </section>
          <section className="history-streaks" aria-label="Study streaks">
            <HistoryMetric icon={<Flame size={17} />} label="Current streak" value={`${streak.currentStreakDays} ${streak.currentStreakDays === 1 ? "day" : "days"}`} />
            <HistoryMetric icon={<Flame size={17} />} label="Longest streak" value={`${streak.longestStreakDays} ${streak.longestStreakDays === 1 ? "day" : "days"}`} />
          </section>
          <section className="panel history-chart-panel">
            <div className="panel-heading"><div><h2>Planned vs actual study time</h2><p>Actual time includes only explicitly recorded minutes</p></div></div>
            {chartData.length === 0 ? <EmptyState icon={<Clock3 size={22} />} title="No study time in this range" description="Tasks will appear here after you plan work in the selected dates." /> : (
              <div className="history-chart" role="img" aria-label="Daily planned and actual task minutes bar chart">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData} margin={{ top: 8, right: 12, bottom: 8, left: 0 }}>
                    <CartesianGrid stroke="#edf0f5" vertical={false} />
                    <XAxis dataKey="date" tick={{ fill: "#858da0", fontSize: 10 }} />
                    <YAxis allowDecimals={false} tick={{ fill: "#858da0", fontSize: 10 }} />
                    <Tooltip formatter={(value) => [`${value} min`]} />
                    <Bar dataKey="planned" name="Planned" fill="#777be7" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="actual" name="Actual" fill="#4aab88" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>
          <section className="panel history-days-panel">
            <div className="panel-heading"><div><h2>Tasks by planned date</h2><p>{formatDate(appliedRange.startDate)} – {formatDate(appliedRange.endDate)}</p></div></div>
            {days.length === 0 ? <EmptyState icon={<CalendarClock size={22} />} title="No tasks in this date range" description="Choose another range or plan a task to begin building history." /> : (
              <div className="history-day-list">
                {days.map((day) => (
                  <section className="history-day" key={day.date}>
                    <div className="history-day-heading">
                      <div><h3>{formatDate(day.date)}</h3><p>{day.completedCount}/{day.taskCount} completed · {day.completionRate.toFixed(0)}%</p></div>
                      <div><span>{formatMinutes(day.plannedMinutes)} planned</span><span>{formatMinutes(day.actualMinutes)} actual</span></div>
                    </div>
                    {day.tasks.map((task) => <div className="history-task-row" key={task.id}><span>{task.title} <small>{task.subject}</small></span><StatusBadge status={task.status} /></div>)}
                  </section>
                ))}
              </div>
            )}
          </section>
          <section className="panel overdue-panel">
            <div className="panel-heading"><div><h2>Overdue tasks</h2><p>Past-dated tasks that are not completed. Rescheduling keeps the same task and history.</p></div><span className="panel-icon"><RotateCcw size={17} /></span></div>
            {overdue.length === 0 ? <EmptyState icon={<CalendarClock size={22} />} title="No overdue tasks" description="Uncompleted tasks from earlier dates will appear here for rescheduling." /> : (
              <div className="overdue-list">
                {overdue.map((task) => (
                  <article className="overdue-row" key={task.id}>
                    <div className="overdue-task-info"><strong>{task.title}</strong><span>{task.subject} · {formatDate(task.plannedDate)} · {task.status.replace("_", " ")}</span></div>
                    <label className="field"><span>New planned date</span><input aria-label={`New planned date for ${task.title}`} type="date" value={rescheduleDates[task.id] || ""} onChange={(event) => setRescheduleDates((current) => ({ ...current, [task.id]: event.target.value }))} /></label>
                    <Button variant="secondary" onClick={() => void reschedule(task)}>Reschedule</Button>
                  </article>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </>
  );
}

function HistoryMetric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string | number }) {
  return <article className="history-metric-card"><span className="history-metric-icon">{icon}</span><span>{label}</span><strong>{value}</strong></article>;
}
