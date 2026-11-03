import { Suspense, lazy, useEffect, useState } from "react";
import {
  ArrowRight,
  CheckCircle2,
  BookOpen,
  Clock3,
  GraduationCap,
  ListTodo,
  Plus,
  Target,
} from "lucide-react";
import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import { api } from "../api/client";
import type { WeeklyStudyDay } from "../components/DashboardCharts";
import type {
  DailyTask,
  DailyTaskSummary,
  Goal,
  ProgressLog,
  ProgressSummary,
  StudentRecord,
} from "../types";
import { EmptyState, ErrorState, formatDate, formatMinutes, LoadingState, PageHeader } from "../components/ui";
import { ProgressBar } from "../components/ProgressBar";
const DashboardCharts = lazy(() => import("../components/DashboardCharts").then((module) => ({ default: module.DashboardCharts })));

interface DashboardData {
  goals: Goal[];
  summaries: ProgressSummary[];
  recentLogs: ProgressLog[];
  tasks: DailyTask[];
  records: StudentRecord[];
  todaySummary: DailyTaskSummary;
  weeklyProgress: WeeklyStudyDay[];
}

async function loadDashboard(): Promise<DashboardData> {
  await api.initializeSampleData();
  const [goals, tasks, records, todaySummary] = await Promise.all([
    api.listGoals(),
    api.listTasks(),
    api.listRecords(),
    api.getTodayTaskSummary(),
  ]);
  const [summaries, logGroups] = await Promise.all([
    Promise.all(goals.map((goal) => api.getProgress(goal.id))),
    Promise.all(goals.map((goal) => api.listLogs(goal.id))),
  ]);
  const logs = logGroups.flat();
  const recentLogs = [...logs]
    .sort((a, b) => b.activityDate.localeCompare(a.activityDate) || b.id - a.id)
    .slice(0, 5);
  return {
    goals,
    summaries,
    recentLogs,
    tasks,
    records,
    todaySummary,
    weeklyProgress: makeWeeklyProgress(logs, todaySummary.date),
  };
}

function makeWeeklyProgress(logs: ProgressLog[], asOfDate: string): WeeklyStudyDay[] {
  const today = new Date(`${asOfDate}T12:00:00`);
  const monday = new Date(today);
  monday.setDate(today.getDate() - ((today.getDay() + 6) % 7));

  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(monday);
    date.setDate(monday.getDate() + index);
    const dateKey = [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, "0"),
      String(date.getDate()).padStart(2, "0"),
    ].join("-");
    return {
      date: dateKey,
      label: new Intl.DateTimeFormat(undefined, { weekday: "short" }).format(date),
      minutes: logs
        .filter((log) => log.activityDate === dateKey)
        .reduce((sum, log) => sum + log.timeSpentMinutes, 0),
    };
  });
}

export function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<unknown>(null);

  const refresh = () => {
    setError(null);
    void loadDashboard().then(setData).catch(setError);
  };
  useEffect(refresh, []);

  const activeGoalSummaries = data?.summaries.filter((summary) =>
    data.goals.some((goal) => goal.id === summary.goalId && goal.status !== "CANCELLED"),
  ) || [];
  const weekly = data?.summaries.reduce((sum, summary) => sum + summary.currentWeekMinutes, 0) || 0;
  const monthly = data?.summaries.reduce((sum, summary) => sum + summary.currentMonthMinutes, 0) || 0;
  const totalMinutes = activeGoalSummaries.reduce((sum, summary) => sum + summary.totalRecordedMinutes, 0);
  const targetMinutes = activeGoalSummaries.reduce((sum, summary) => sum + summary.targetDurationMinutes, 0);
  const goalCompletion = targetMinutes > 0
    ? Math.min(100, Math.round((totalMinutes / targetMinutes) * 1000) / 10)
    : 0;

  if (error) return <><PageHeader eyebrow="YOUR LEARNING SPACE" title="Overview" /><ErrorState error={error} onRetry={refresh} /></>;
  if (!data) return <LoadingState label="Gathering your progress…" />;

  const pendingTasks = data.tasks.filter((task) => task.status === "PENDING").length;
  const completedTasks = data.tasks.filter((task) => task.status === "COMPLETED").length;
  const upcomingTasks = data.tasks
    .filter((task) => task.status !== "COMPLETED" && task.plannedDate >= data.todaySummary.date)
    .sort((a, b) => a.plannedDate.localeCompare(b.plannedDate) || a.id - b.id)
    .slice(0, 5);

  return (
    <>
      <PageHeader
        eyebrow="YOUR LEARNING SPACE"
        title="Your week, in focus."
        description="A clear view of your study time, priorities, and progress."
        action={<Link className="button button-primary" to="/goals/new"><Plus size={17} /> New goal</Link>}
      />
      <section className="metric-grid" aria-label="Progress overview">
        <MetricCard icon={<GraduationCap size={19} />} label="Student records" value={`${data.records.length}`} detail="Academic milestones and work" tone="indigo" />
        <MetricCard icon={<CheckCircle2 size={19} />} label="Tasks completed" value={`${completedTasks}`} detail="Across your task plan" tone="green" />
        <MetricCard icon={<ListTodo size={19} />} label="Tasks pending" value={`${pendingTasks}`} detail="Not yet started" tone="amber" />
        <MetricCard icon={<Clock3 size={19} />} label="Study time today" value={formatMinutes(data.todaySummary.actualMinutes)} detail={`${formatMinutes(weekly)} logged this week`} tone="blue" />
        <MetricCard icon={<Target size={19} />} label="Goal completion" value={`${goalCompletion}%`} detail={`${data.goals.length} goals · ${formatMinutes(monthly)} this month`} tone="indigo" />
      </section>

      <Suspense fallback={<LoadingState label="Preparing your charts…" />}>
        <DashboardCharts
          goals={data.goals}
          weekly={weekly}
          monthly={monthly}
          weeklyProgress={data.weeklyProgress}
        />
      </Suspense>

      <section className="dashboard-grid lower-grid">
        <div className="panel">
          <div className="panel-heading">
            <div><h2>Goals in progress</h2><p>Keep moving at your own pace</p></div>
            <Link to="/goals" className="text-link">All goals <ArrowRight size={14} /></Link>
          </div>
          {data.goals.filter((goal) => goal.status === "IN_PROGRESS").length === 0 ? (
            <EmptyState icon={<Target size={22} />} title="Nothing in progress" description="Start a goal when you're ready to make time for it." action={<Link className="button button-secondary" to="/goals/new">Add a goal</Link>} />
          ) : (
            <div className="goal-progress-list">
              {data.summaries.filter((summary) => data.goals.some((goal) => goal.id === summary.goalId && goal.status === "IN_PROGRESS")).slice(0, 4).map((summary) => (
                <Link to={`/goals/${summary.goalId}`} className="goal-progress-row" key={summary.goalId}>
                  <div className="goal-row-top"><strong>{summary.goalTitle}</strong><span>{summary.progressPercentage}%</span></div>
                  <ProgressBar value={summary.progressPercentage} label={`${summary.goalTitle} time target`} />
                  <small>{formatMinutes(summary.totalRecordedMinutes)} recorded <span>·</span> {formatMinutes(summary.remainingMinutes)} remaining</small>
                </Link>
              ))}
            </div>
          )}
        </div>
        <div className="panel">
          <div className="panel-heading">
            <div><h2>Coming up</h2><p>Your next planned study sessions</p></div>
            <Link to="/tasks" className="text-link">Task planner <ArrowRight size={14} /></Link>
          </div>
          {upcomingTasks.length === 0 ? (
            <EmptyState
              icon={<ListTodo size={22} />}
              title="Your schedule is clear"
              description="Add a task when you are ready to plan your next study session."
              action={<Link className="button button-secondary" to="/tasks">Open task planner</Link>}
            />
          ) : (
            <div className="upcoming-task-list">
              {upcomingTasks.map((task) => (
                <UpcomingTask key={task.id} task={task} today={data.todaySummary.date} />
              ))}
            </div>
          )}
        </div>
        <div className="panel dashboard-activity-panel">
          <div className="panel-heading">
            <div><h2>Recent activity</h2><p>Study sessions you’ve logged lately</p></div>
            <span className="panel-icon"><BookOpen size={17} /></span>
          </div>
          {data.recentLogs.length === 0 ? (
            <EmptyState icon={<BookOpen size={22} />} title="Your first session is waiting" description="Log the work you complete on any goal." />
          ) : (
            <div className="recent-list">
              {data.recentLogs.map((log) => {
                const goal = data.goals.find((item) => item.id === log.goalId);
                return (
                  <Link to={`/goals/${log.goalId}`} className="recent-row" key={log.id}>
                    <span className="recent-icon"><Clock3 size={16} /></span>
                    <span className="recent-copy"><strong>{log.summary}</strong><small>{goal?.title || "Goal"} · {formatDate(log.activityDate)}</small></span>
                    <span className="recent-time">{formatMinutes(log.timeSpentMinutes)}</span>
                  </Link>
                );
              })}
            </div>
          )}
        </div>
      </section>
      <p className="metric-disclaimer">Goal completion is time recorded against your target durations. Today’s study time uses actual minutes recorded on today’s tasks.</p>
    </>
  );
}

function UpcomingTask({ task, today }: { task: DailyTask; today: string }) {
  const dueLabel = task.plannedDate === today
    ? "Today"
    : formatDate(task.plannedDate);

  return (
    <Link to="/tasks" className="upcoming-task-row">
      <span className={`upcoming-task-status upcoming-task-${task.status.toLowerCase()}`} aria-hidden="true" />
      <span className="upcoming-task-copy">
        <strong>{task.title}</strong>
        <small>{task.subject} · {dueLabel}</small>
      </span>
      <span className="upcoming-task-duration">{formatMinutes(task.estimatedDurationMinutes)}</span>
    </Link>
  );
}

function MetricCard({
  icon,
  label,
  value,
  detail,
  tone,
}: {
  icon: ReactNode;
  label: string;
  value: string;
  detail: string;
  tone: string;
}) {
  return (
    <article className="metric-card">
      <div className={`metric-icon metric-${tone}`}>{icon}</div>
      <p>{label}</p>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}
