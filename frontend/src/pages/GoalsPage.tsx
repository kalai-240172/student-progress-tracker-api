import { useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, CalendarDays, Clock3, Flag, Plus, Trash2 } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { Goal, GoalRequest, ProgressLog, ProgressLogRequest, ProgressSummary } from "../types";
import { GoalForm } from "../components/GoalForm";
import { ProgressLogForm } from "../components/ProgressLogForm";
import { ProgressBar } from "../components/ProgressBar";
import { Button, EmptyState, ErrorState, formatDate, formatLabel, formatMinutes, LoadingState, PageHeader, StatusBadge } from "../components/ui";

interface GoalWithSummary {
  goal: Goal;
  summary: ProgressSummary;
}

export function GoalsPage() {
  const [goals, setGoals] = useState<GoalWithSummary[] | null>(null);
  const [error, setError] = useState<unknown>(null);

  const load = () => {
    setError(null);
    void api.listGoals()
      .then(async (items) => Promise.all(items.map(async (goal) => ({
        goal,
        summary: await api.getProgress(goal.id),
      }))))
      .then(setGoals)
      .catch(setError);
  };
  useEffect(load, []);

  if (error) return <><PageHeader eyebrow="YOUR LEARNING PLAN" title="Goals" /><ErrorState error={error} onRetry={load} /></>;
  if (!goals) return <LoadingState label="Loading your goals…" />;

  const active = goals.filter(({ goal }) => goal.status === "IN_PROGRESS" || goal.status === "NOT_STARTED");
  const finished = goals.filter(({ goal }) => goal.status === "COMPLETED" || goal.status === "CANCELLED");

  return (
    <>
      <PageHeader
        eyebrow="YOUR LEARNING PLAN"
        title="Goals"
        description="Make time for the things you want to learn."
        action={<Link className="button button-primary" to="/goals/new"><Plus size={17} /> New goal</Link>}
      />
      {goals.length === 0 ? (
        <div className="panel"><EmptyState icon={<Flag size={23} />} title="Give your learning a direction" description="Create a goal, choose a time target, and track each focused session." action={<Link className="button button-primary" to="/goals/new"><Plus size={17} /> Create your first goal</Link>} /></div>
      ) : (
        <>
          {active.length > 0 && <GoalGroup title="In your plan" goals={active} />}
          {finished.length > 0 && <GoalGroup title="Completed & cancelled" goals={finished} />}
          <p className="metric-disclaimer">Progress percentages show time recorded against your target duration — not a measure of learning quality.</p>
        </>
      )}
    </>
  );
}

function GoalGroup({ title, goals }: { title: string; goals: GoalWithSummary[] }) {
  return (
    <section className="goal-group">
      <div className="section-heading"><h2>{title}</h2><span>{goals.length}</span></div>
      <div className="goal-card-grid">
        {goals.map(({ goal, summary }) => (
          <article className={`goal-card ${goal.status === "CANCELLED" ? "goal-card-muted" : ""}`} key={goal.id}>
            <div className="goal-card-heading">
              <span className={`goal-category-icon goal-icon-${goal.category.toLowerCase()}`}><Flag size={17} /></span>
              <StatusBadge status={goal.status} />
            </div>
            <p className="record-category">{formatLabel(goal.category)}</p>
            <h3><Link to={`/goals/${goal.id}`}>{goal.title}</Link></h3>
            <p className="goal-card-description">{goal.description || "A personal learning goal."}</p>
            <div className="goal-progress-meta"><span>Time target</span><strong>{summary.progressPercentage}%</strong></div>
            <ProgressBar value={summary.progressPercentage} label={`${goal.title} time target`} />
            <div className="goal-time-meta"><span>{formatMinutes(summary.totalRecordedMinutes)} recorded</span><span>{formatMinutes(summary.remainingMinutes)} left</span></div>
            <div className="goal-card-footer">
              <span><Clock3 size={14} /> {formatMinutes(goal.targetDurationMinutes)} target</span>
              {goal.targetDate && <span><CalendarDays size={14} /> {formatDate(goal.targetDate)}</span>}
              <Link to={`/goals/${goal.id}`} className="icon-link" aria-label={`View ${goal.title}`}><ArrowRight size={17} /></Link>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

export function GoalFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [goal, setGoal] = useState<Goal | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(Boolean(id));

  useEffect(() => {
    if (!id) return;
    const goalId = Number(id);
    if (!Number.isSafeInteger(goalId) || goalId <= 0) {
      setError(new Error("Invalid goal address."));
      setLoading(false);
      return;
    }
    void api.getGoal(goalId).then(setGoal).catch(setError).finally(() => setLoading(false));
  }, [id]);

  if (loading) return <LoadingState label="Loading goal…" />;
  if (error && id) return <><PageHeader title="Goal" /><ErrorState error={error} /></>;

  const save = async (request: GoalRequest) => {
    if (id) await api.updateGoal(Number(id), request);
    else await api.createGoal(request);
    navigate("/goals");
  };

  return (
    <>
      <Link to="/goals" className="back-link"><ArrowLeft size={16} /> Back to goals</Link>
      <PageHeader eyebrow="YOUR LEARNING PLAN" title={id ? "Edit goal" : "Create a goal"} description="Choose a clear target and a rhythm that works for you." />
      <div className="form-panel panel">
        {error !== null && <ErrorState error={error} />}
        <GoalForm initial={goal || undefined} onSubmit={save} onCancel={() => navigate("/goals")} />
      </div>
    </>
  );
}

interface GoalDetailData {
  goal: Goal;
  summary: ProgressSummary;
  logs: ProgressLog[];
}

export function GoalDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const goalId = Number(id);
  const [data, setData] = useState<GoalDetailData | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [editingLog, setEditingLog] = useState<ProgressLog | null>(null);
  const [showLogForm, setShowLogForm] = useState(false);
  const [deletingGoal, setDeletingGoal] = useState(false);

  const load = () => {
    setError(null);
    if (!Number.isSafeInteger(goalId) || goalId <= 0) {
      setError(new Error("Invalid goal address."));
      return;
    }
    void Promise.all([api.getGoal(goalId), api.getProgress(goalId), api.listLogs(goalId)])
      .then(([goal, summary, logs]) => setData({ goal, summary, logs }))
      .catch(setError);
  };
  useEffect(load, [goalId]);

  const saveLog = async (request: ProgressLogRequest) => {
    setError(null);
    try {
      if (editingLog) await api.updateLog(goalId, editingLog.id, request);
      else await api.createLog(goalId, request);
      setEditingLog(null);
      setShowLogForm(false);
      load();
    } catch (saveError) {
      setError(saveError);
    }
  };

  const deleteLog = async (log: ProgressLog) => {
    if (!window.confirm("Delete this progress entry? The goal totals will be recalculated.")) return;
    setError(null);
    try {
      await api.deleteLog(goalId, log.id);
      load();
    } catch (deleteError) {
      setError(deleteError);
    }
  };

  const deleteGoal = async () => {
    if (!data || !window.confirm(`Delete “${data.goal.title}” and all its progress logs? This cannot be undone.`)) return;
    setDeletingGoal(true);
    try {
      await api.deleteGoal(goalId);
      navigate("/goals");
    } catch (deleteError) {
      setError(deleteError);
      setDeletingGoal(false);
    }
  };

  if (error && !data) return <><PageHeader title="Goal details" /><ErrorState error={error} onRetry={load} /></>;
  if (!data) return <LoadingState label="Loading your goal…" />;

  return (
    <>
      <Link to="/goals" className="back-link"><ArrowLeft size={16} /> Back to goals</Link>
      <PageHeader
        eyebrow={formatLabel(data.goal.category)}
        title={data.goal.title}
        description={data.goal.description || "Your personal learning goal."}
        action={<div className="header-actions"><Link className="button button-secondary" to={`/goals/${goalId}/edit`}>Edit goal</Link><Button variant="danger" onClick={() => void deleteGoal()} disabled={deletingGoal}><Trash2 size={16} /> {deletingGoal ? "Deleting…" : "Delete"}</Button></div>}
      />
      {error !== null && <ErrorState error={error} />}
      <div className="goal-detail-status"><StatusBadge status={data.goal.status} />{data.goal.targetDate && <span><CalendarDays size={15} /> Target date {formatDate(data.goal.targetDate)}</span>}</div>
      <section className="summary-grid">
        <SummaryCard label="Time recorded" value={formatMinutes(data.summary.totalRecordedMinutes)} detail={`${data.summary.totalLogCount} logged ${data.summary.totalLogCount === 1 ? "session" : "sessions"}`} />
        <SummaryCard label="Time remaining" value={formatMinutes(data.summary.remainingMinutes)} detail={`of ${formatMinutes(data.summary.targetDurationMinutes)} target`} />
        <SummaryCard label="This week" value={formatMinutes(data.summary.currentWeekMinutes)} detail="Monday through Sunday" />
        <SummaryCard label="This month" value={formatMinutes(data.summary.currentMonthMinutes)} detail="Current calendar month" />
      </section>
      <section className="panel goal-summary-panel">
        <div className="goal-progress-meta"><div><h2>Duration target</h2><p>Time logged toward this goal’s target</p></div><strong className="large-percent">{data.summary.progressPercentage}%</strong></div>
        <ProgressBar value={data.summary.progressPercentage} label={`${data.goal.title} time target`} />
        <div className="summary-footer"><span>{formatMinutes(data.summary.totalRecordedMinutes)} logged</span><span>{formatMinutes(data.summary.targetDurationMinutes)} total target</span></div>
        <p className="metric-disclaimer">This percentage reflects time logged, not learning quality.</p>
      </section>
      <section className="panel logs-panel">
        <div className="panel-heading">
          <div><h2>Daily progress</h2><p>Capture the effort behind your progress</p></div>
          {!showLogForm && !editingLog && <Button onClick={() => setShowLogForm(true)}><Plus size={16} /> Log a session</Button>}
        </div>
        {(showLogForm || editingLog) && (
          <div className="log-form-wrap">
            <h3>{editingLog ? "Edit progress entry" : "Add a progress entry"}</h3>
            <ProgressLogForm
              key={editingLog?.id || "new"}
              initial={editingLog || undefined}
              onSubmit={saveLog}
              onCancel={() => { setEditingLog(null); setShowLogForm(false); }}
            />
          </div>
        )}
        {data.logs.length === 0 ? (
          <EmptyState icon={<Clock3 size={22} />} title="No sessions logged yet" description="Add an entry when you spend time working toward this goal." action={!showLogForm ? <Button variant="secondary" onClick={() => setShowLogForm(true)}><Plus size={16} /> Log your first session</Button> : undefined} />
        ) : (
          <div className="log-list">
            {data.logs.map((log) => (
              <article className="log-row" key={log.id}>
                <span className="log-date"><strong>{new Intl.DateTimeFormat(undefined, { day: "2-digit" }).format(new Date(`${log.activityDate}T12:00:00`))}</strong><small>{new Intl.DateTimeFormat(undefined, { month: "short" }).format(new Date(`${log.activityDate}T12:00:00`))}</small></span>
                <div className="log-content"><div className="log-heading"><h3>{log.summary}</h3><span>{formatMinutes(log.timeSpentMinutes)}</span></div><p>{log.notes || "No additional notes."}</p><small>{formatDate(log.activityDate)}</small></div>
                <div className="log-actions"><button className="text-button" onClick={() => { setEditingLog(log); setShowLogForm(false); }}>Edit</button><button className="text-button text-danger" onClick={() => void deleteLog(log)}>Delete</button></div>
              </article>
            ))}
          </div>
        )}
      </section>
    </>
  );
}

function SummaryCard({ label, value, detail }: { label: string; value: string; detail: string }) {
  return <article className="summary-card"><span>{label}</span><strong>{value}</strong><small>{detail}</small></article>;
}
