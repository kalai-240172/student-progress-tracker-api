import { Suspense, lazy, useEffect, useState } from "react";
import {
  ArrowRight,
  BookOpen,
  Clock3,
  Flame,
  Plus,
  Target,
  TrendingUp,
} from "lucide-react";
import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import { api } from "../api/client";
import type { Goal, ProgressLog, ProgressSummary } from "../types";
import { EmptyState, ErrorState, formatDate, formatMinutes, LoadingState, PageHeader } from "../components/ui";
import { ProgressBar } from "../components/ProgressBar";
const DashboardCharts = lazy(() => import("../components/DashboardCharts").then((module) => ({ default: module.DashboardCharts })));

interface DashboardData {
  goals: Goal[];
  summaries: ProgressSummary[];
  recentLogs: ProgressLog[];
}

async function loadDashboard(): Promise<DashboardData> {
  const goals = await api.listGoals();
  const [summaries, logGroups] = await Promise.all([
    Promise.all(goals.map((goal) => api.getProgress(goal.id))),
    Promise.all(goals.map((goal) => api.listLogs(goal.id))),
  ]);
  const recentLogs = logGroups.flat().sort((a, b) => b.activityDate.localeCompare(a.activityDate)).slice(0, 5);
  return { goals, summaries, recentLogs };
}

export function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<unknown>(null);

  const refresh = () => {
    setError(null);
    void loadDashboard().then(setData).catch(setError);
  };
  useEffect(refresh, []);

  const weekly = data?.summaries.reduce((sum, summary) => sum + summary.currentWeekMinutes, 0) || 0;
  const monthly = data?.summaries.reduce((sum, summary) => sum + summary.currentMonthMinutes, 0) || 0;
  const totalMinutes = data?.summaries.reduce((sum, summary) => sum + summary.totalRecordedMinutes, 0) || 0;

  if (error) return <><PageHeader eyebrow="YOUR LEARNING SPACE" title="Overview" /><ErrorState error={error} onRetry={refresh} /></>;
  if (!data) return <LoadingState label="Gathering your progress…" />;

  return (
    <>
      <PageHeader
        eyebrow="YOUR LEARNING SPACE"
        title="A little progress adds up."
        description="Here’s your learning at a glance."
        action={<Link className="button button-primary" to="/goals/new"><Plus size={17} /> New goal</Link>}
      />
      <section className="metric-grid" aria-label="Progress overview">
        <MetricCard icon={<Target size={19} />} label="Active goals" value={`${data.goals.filter((goal) => goal.status === "IN_PROGRESS").length}`} detail={`${data.goals.length} total goals`} tone="indigo" />
        <MetricCard icon={<Clock3 size={19} />} label="Time recorded" value={formatMinutes(totalMinutes)} detail="Across all your goals" tone="blue" />
        <MetricCard icon={<Flame size={19} />} label="This week" value={formatMinutes(weekly)} detail="Monday through today" tone="amber" />
        <MetricCard icon={<TrendingUp size={19} />} label="This month" value={formatMinutes(monthly)} detail="Current calendar month" tone="green" />
      </section>

      <Suspense fallback={<LoadingState label="Preparing your charts…" />}>
        <DashboardCharts goals={data.goals} weekly={weekly} monthly={monthly} />
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
            <div><h2>Recent sessions</h2><p>Work you’ve logged lately</p></div>
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
      <p className="metric-disclaimer">Goal percentages show time recorded against your target duration — not a measure of learning quality.</p>
    </>
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
