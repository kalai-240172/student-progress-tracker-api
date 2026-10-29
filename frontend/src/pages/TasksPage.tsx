import { useCallback, useEffect, useState } from "react";
import { CalendarDays, Check, Clock3, ListTodo, Pencil, Plus, Trash2 } from "lucide-react";
import { api } from "../api/client";
import type { DailyTask, DailyTaskRequest, DailyTaskStatus, DailyTaskSummary } from "../types";
import { DailyTaskForm } from "../components/DailyTaskForm";
import { Button, EmptyState, ErrorState, formatDate, formatLabel, formatMinutes, LoadingState, PageHeader, StatusBadge } from "../components/ui";

type TaskFilter = "today" | "all" | "date";

function localDateValue(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function TasksPage() {
  const [tasks, setTasks] = useState<DailyTask[] | null>(null);
  const [summary, setSummary] = useState<DailyTaskSummary | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [filter, setFilter] = useState<TaskFilter>("today");
  const [selectedDate, setSelectedDate] = useState(localDateValue(new Date()));
  const [editing, setEditing] = useState<DailyTask | "new" | null>(null);
  const [busyTaskId, setBusyTaskId] = useState<number | null>(null);
  const [actualValues, setActualValues] = useState<Record<number, string>>({});

  const load = useCallback(() => {
    setError(null);
    const filters = filter === "today"
      ? { date: localDateValue(new Date()) }
      : filter === "date" && selectedDate ? { date: selectedDate } : {};
    void Promise.all([api.listTasks(filters), api.getTodayTaskSummary()])
      .then(([items, today]) => {
        setTasks(items);
        setSummary(today);
        setActualValues(Object.fromEntries(items.map((task) => [
          task.id,
          task.actualDurationMinutes === null ? "" : String(task.actualDurationMinutes),
        ])));
      })
      .catch(setError);
  }, [filter, selectedDate]);

  useEffect(() => { load(); }, [load]);

  const save = async (request: DailyTaskRequest) => {
    if (editing && editing !== "new") await api.updateTask(editing.id, request);
    else await api.createTask(request);
    setEditing(null);
    load();
  };

  const changeStatus = async (task: DailyTask, status: DailyTaskStatus) => {
    setBusyTaskId(task.id);
    setError(null);
    try {
      await api.updateTaskStatus(task.id, status);
      load();
    } catch (actionError) {
      setError(actionError);
    } finally {
      setBusyTaskId(null);
    }
  };

  const recordActualDuration = async (task: DailyTask) => {
    const value = actualValues[task.id] ?? "";
    if (!/^\d+$/.test(value)) {
      setError(new Error("Enter a whole number of actual minutes, including zero if appropriate."));
      return;
    }
    setBusyTaskId(task.id);
    setError(null);
    try {
      await api.updateTaskActualDuration(task.id, Number(value));
      load();
    } catch (actionError) {
      setError(actionError);
    } finally {
      setBusyTaskId(null);
    }
  };

  const deleteTask = async (task: DailyTask) => {
    if (!window.confirm(`Delete “${task.title}”? This cannot be undone.`)) return;
    setBusyTaskId(task.id);
    setError(null);
    try {
      await api.deleteTask(task.id);
      load();
      setBusyTaskId(null);
    } catch (actionError) {
      setError(actionError);
      setBusyTaskId(null);
    }
  };

  const displayDate = filter === "today" ? summary?.date : filter === "date" ? selectedDate : null;

  return (
    <>
      <PageHeader
        eyebrow="DAILY PLANNING"
        title="Daily tasks"
        description="Plan focused work, record the time spent, and keep your day on track."
        action={<Button onClick={() => setEditing("new")}><Plus size={17} /> New task</Button>}
      />
      {error !== null && <ErrorState error={error} onRetry={load} />}
      {summary && (
        <section className="task-summary-grid" aria-label="Today's task summary">
          <TaskMetric icon={<ListTodo size={17} />} label="Today's tasks" value={summary.taskCount} />
          <TaskMetric icon={<Check size={17} />} label="Completed" value={summary.completedCount} />
          <TaskMetric icon={<Clock3 size={17} />} label="Planned time" value={formatMinutes(summary.plannedMinutes)} />
          <TaskMetric icon={<Clock3 size={17} />} label="Actual time" value={formatMinutes(summary.actualMinutes)} />
        </section>
      )}
      {editing && (
        <section className="panel task-editor">
          <div className="panel-heading">
            <div><h2>{editing === "new" ? "Plan a task" : "Edit task"}</h2><p>Changing the planned date reschedules the same task.</p></div>
          </div>
          <DailyTaskForm
            key={editing === "new" ? "new-task" : editing.id}
            initial={editing === "new" ? undefined : editing}
            onSubmit={save}
            onCancel={() => setEditing(null)}
          />
        </section>
      )}
      <section className="panel task-list-panel">
        <div className="panel-heading task-list-heading">
          <div><h2>{filter === "today" ? "Today's plan" : filter === "all" ? "All tasks" : "Tasks for selected date"}</h2><p>{displayDate ? formatDate(displayDate) : "Tasks sorted by planned date"}</p></div>
          <div className="task-filters">
            <label className="field">
              <span className="sr-only">Task list filter</span>
              <select aria-label="Task list filter" value={filter} onChange={(event) => setFilter(event.target.value as TaskFilter)}>
                <option value="today">Today</option>
                <option value="all">All tasks</option>
                <option value="date">Choose date</option>
              </select>
            </label>
            {filter === "date" && (
              <label className="field">
                <span className="sr-only">Filter tasks by date</span>
                <input aria-label="Filter tasks by date" type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} />
              </label>
            )}
          </div>
        </div>
        {tasks === null ? (
          <LoadingState label="Loading your tasks…" />
        ) : tasks.length === 0 ? (
          <EmptyState
            icon={<CalendarDays size={23} />}
            title={filter === "today" ? "Nothing planned for today" : "No tasks found"}
            description={filter === "today" ? "Add a task when you are ready to plan your study time." : "Try another date or add a new task."}
            action={filter === "today" && !editing ? <Button onClick={() => setEditing("new")}><Plus size={16} /> Plan your first task</Button> : undefined}
          />
        ) : (
          <div className="daily-task-list">
            {tasks.map((task) => (
              <article className={`daily-task-card ${task.status === "COMPLETED" ? "daily-task-complete" : ""}`} key={task.id}>
                <div className="daily-task-main">
                  <div className="daily-task-title-row"><h3>{task.title}</h3><StatusBadge status={task.status} /></div>
                  <p>{task.subject} · {formatDate(task.plannedDate)}</p>
                  {task.description && <p className="daily-task-description">{task.description}</p>}
                  <div className="daily-task-time"><span>Estimated {formatMinutes(task.estimatedDurationMinutes)}</span><span>Actual {task.actualDurationMinutes === null ? "Not recorded" : formatMinutes(task.actualDurationMinutes)}</span></div>
                </div>
                <div className="daily-task-actions">
                  <label className="field task-status-select">
                    <span>Status</span>
                    <select
                      aria-label={`Status for ${task.title}`}
                      value={task.status}
                      disabled={busyTaskId === task.id}
                      onChange={(event) => void changeStatus(task, event.target.value as DailyTaskStatus)}
                    >
                      {(["PENDING", "IN_PROGRESS", "COMPLETED"] as const).map((status) => (
                        <option key={status} value={status}>{formatLabel(status)}</option>
                      ))}
                    </select>
                  </label>
                  <label className="field task-actual-input">
                    <span>Actual minutes</span>
                    <input
                      aria-label={`Actual minutes for ${task.title}`}
                      type="number"
                      min={0}
                      step={1}
                      value={actualValues[task.id] ?? ""}
                      onChange={(event) => setActualValues((current) => ({ ...current, [task.id]: event.target.value }))}
                    />
                  </label>
                  <Button variant="secondary" disabled={busyTaskId === task.id} onClick={() => void recordActualDuration(task)}>Save time</Button>
                  <Button variant="ghost" aria-label={`Edit ${task.title}`} disabled={busyTaskId === task.id} onClick={() => setEditing(task)}><Pencil size={16} /></Button>
                  <Button variant="danger" aria-label={`Delete ${task.title}`} disabled={busyTaskId === task.id} onClick={() => void deleteTask(task)}><Trash2 size={16} /></Button>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </>
  );
}

function TaskMetric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string | number }) {
  return <article className="task-summary-card"><span className="task-summary-icon">{icon}</span><span>{label}</span><strong>{value}</strong></article>;
}
