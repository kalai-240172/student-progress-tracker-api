import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, ArrowRight, CalendarDays, CheckCircle2, Circle } from "lucide-react";
import { api } from "../api/client";
import type { DailyTask } from "../types";
import { Button, EmptyState, ErrorState, formatDate, formatMinutes, LoadingState, PageHeader, StatusBadge } from "../components/ui";
import { calendarDates, calendarRange, formatLocalDate, navigateCalendar, parseLocalDate, type CalendarView } from "../tasks/dateUtils";

const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

export function CalendarPage() {
  const [anchor, setAnchor] = useState(() => new Date());
  const [view, setView] = useState<CalendarView>("month");
  const [selectedDate, setSelectedDate] = useState(formatLocalDate(new Date()));
  const [tasks, setTasks] = useState<DailyTask[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const range = useMemo(() => calendarRange(anchor, view), [anchor, view]);
  const dates = useMemo(() => calendarDates(anchor, view), [anchor, view]);

  const load = useCallback(() => {
    setError(null);
    setTasks(null);
    void api.listTasks(range).then(setTasks).catch(setError);
  }, [range.startDate, range.endDate]);
  useEffect(() => { load(); }, [load]);

  const byDate = useMemo(() => {
    const grouped = new Map<string, DailyTask[]>();
    for (const task of tasks || []) {
      const group = grouped.get(task.plannedDate) || [];
      group.push(task);
      grouped.set(task.plannedDate, group);
    }
    return grouped;
  }, [tasks]);
  const selectedTasks = byDate.get(selectedDate) || [];
  const periodLabel = view === "month"
    ? new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" }).format(anchor)
    : `${formatDate(range.startDate)} – ${formatDate(range.endDate)}`;

  const movePeriod = (direction: -1 | 1) => {
    const next = navigateCalendar(anchor, view, direction);
    setAnchor(next);
    setSelectedDate(formatLocalDate(next));
  };
  const goToToday = () => {
    const today = new Date();
    setAnchor(today);
    setSelectedDate(formatLocalDate(today));
  };
  const setCalendarView = (nextView: CalendarView) => {
    setView(nextView);
    setAnchor(parseLocalDate(selectedDate));
  };

  if (error && tasks === null) {
    return <><PageHeader eyebrow="PLAN AND REVIEW" title="Calendar" description="See planned work and completion at a glance." /><ErrorState error={error} onRetry={load} /></>;
  }

  return (
    <>
      <PageHeader eyebrow="PLAN AND REVIEW" title="Calendar" description="See planned work and completion at a glance." />
      {error && <ErrorState error={error} onRetry={load} />}
      <section className="panel calendar-panel">
        <div className="calendar-toolbar">
          <div className="calendar-period-control">
            <Button variant="secondary" aria-label="Previous period" onClick={() => movePeriod(-1)}><ArrowLeft size={16} /></Button>
            <h2 aria-live="polite">{periodLabel}</h2>
            <Button variant="secondary" aria-label="Next period" onClick={() => movePeriod(1)}><ArrowRight size={16} /></Button>
            <Button variant="ghost" onClick={goToToday}>Today</Button>
          </div>
          <div className="calendar-view-switch" role="group" aria-label="Calendar view">
            <Button variant={view === "month" ? "primary" : "secondary"} aria-pressed={view === "month"} onClick={() => setCalendarView("month")}>Month</Button>
            <Button variant={view === "week" ? "primary" : "secondary"} aria-pressed={view === "week"} onClick={() => setCalendarView("week")}>Week</Button>
          </div>
        </div>
        {tasks === null ? <LoadingState label="Loading calendar tasks…" /> : (
          <>
            <div className="calendar-grid" role="grid" aria-label={`${view} view ${periodLabel}`}>
              {weekdays.map((day) => <div className="calendar-weekday" role="columnheader" key={day}>{day}</div>)}
              {dates.map((date) => {
                const key = formatLocalDate(date);
                const dayTasks = byDate.get(key) || [];
                const completed = dayTasks.filter((task) => task.status === "COMPLETED").length;
                const inMonth = date.getMonth() === anchor.getMonth();
                return (
                  <button
                    className={`calendar-day ${inMonth || view === "week" ? "" : "calendar-day-muted"} ${selectedDate === key ? "calendar-day-selected" : ""} ${key === formatLocalDate(new Date()) ? "calendar-day-today" : ""}`}
                    type="button"
                    role="gridcell"
                    aria-label={`${formatDate(key)}, ${dayTasks.length} ${dayTasks.length === 1 ? "task" : "tasks"}, ${completed} completed`}
                    aria-pressed={selectedDate === key}
                    key={key}
                    onClick={() => { setSelectedDate(key); if (view === "week") setAnchor(date); }}
                  >
                    <span className="calendar-day-number">{date.getDate()}</span>
                    {dayTasks.length > 0 && (
                      <span className="calendar-day-count">
                        {completed === dayTasks.length ? <CheckCircle2 size={13} aria-label="All complete" /> : <Circle size={11} aria-hidden="true" />}
                        {dayTasks.length} {dayTasks.length === 1 ? "task" : "tasks"}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
            <div className="calendar-selection">
              <div className="panel-heading">
                <div><h2>{formatDate(selectedDate)}</h2><p>{selectedTasks.length} {selectedTasks.length === 1 ? "task" : "tasks"} · {formatMinutes(selectedTasks.reduce((sum, task) => sum + task.estimatedDurationMinutes, 0))} planned</p></div>
                <span className="panel-icon"><CalendarDays size={17} /></span>
              </div>
              {selectedTasks.length === 0 ? (
                <EmptyState icon={<CalendarDays size={22} />} title="No tasks planned" description="There are no tasks scheduled for this date." />
              ) : (
                <div className="calendar-task-list">
                  {selectedTasks.map((task) => (
                    <article className="calendar-task-row" key={task.id}>
                      <div><h3>{task.title}</h3><p>{task.subject} · {formatMinutes(task.estimatedDurationMinutes)} planned{task.actualDurationMinutes === null ? "" : ` · ${formatMinutes(task.actualDurationMinutes)} actual`}</p></div>
                      <StatusBadge status={task.status} />
                    </article>
                  ))}
                </div>
              )}
            </div>
          </>
        )}
      </section>
    </>
  );
}
