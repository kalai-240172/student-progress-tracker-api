import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowUpRight,
  Bell,
  BookOpen,
  CheckCircle2,
  Clock3,
  Pause,
  Play,
  RotateCcw,
  Sparkles,
  Target,
  TimerReset,
} from "lucide-react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import type {
  AcademicSubject,
  AcademicYear,
  AcademicYearSummary,
  FocusSession,
  FocusSessionRequest,
  Goal,
} from "../types";
import {
  EmptyState,
  ErrorState,
  formatDate,
  formatMinutes,
  LoadingState,
  PageHeader,
} from "../components/ui";

const DEFAULT_FOCUS_MINUTES = 25;
const MIN_FOCUS_MINUTES = 5;
const MAX_FOCUS_MINUTES = 120;
const FOCUS_MINUTE_STEP = 5;
const SHORT_BREAK_MINUTES = 5;
const LONG_BREAK_MINUTES = 15;
const DEFAULT_DAILY_GOAL = 4;
const TIMER_STORAGE_KEY = "focus-arena-timer-v1";
const DAILY_GOAL_STORAGE_KEY = "focus-arena-daily-goal-v1";
const CIRCUMFERENCE = 2 * Math.PI * 126;

type TimerMode = "FOCUS" | "SHORT_BREAK" | "LONG_BREAK";

interface TimerState {
  mode: TimerMode;
  focusDurationMinutes: number;
  remainingSeconds: number;
  startedAt: string | null;
  requestKey: string | null;
  subjectId: number | null;
  subjectName: string | null;
  goalId: number | null;
  goalTitle: string | null;
  pendingRequest: FocusSessionRequest | null;
}

interface TimerRestoreResult {
  timer: TimerState;
  error: Error | null;
}

interface FocusData {
  subjects: AcademicSubject[];
  goals: Goal[];
  sessions: FocusSession[];
  today: string;
  weekStart: string;
}

const defaultTimerState: TimerState = {
  mode: "FOCUS",
  focusDurationMinutes: DEFAULT_FOCUS_MINUTES,
  remainingSeconds: DEFAULT_FOCUS_MINUTES * 60,
  startedAt: null,
  requestKey: null,
  subjectId: null,
  subjectName: null,
  goalId: null,
  goalTitle: null,
  pendingRequest: null,
};

function isTimerMode(value: unknown): value is TimerMode {
  return value === "FOCUS" || value === "SHORT_BREAK" || value === "LONG_BREAK";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isValidFocusDuration(value: unknown): value is number {
  return typeof value === "number"
    && Number.isInteger(value)
    && value >= MIN_FOCUS_MINUTES
    && value <= MAX_FOCUS_MINUTES
    && value % FOCUS_MINUTE_STEP === 0;
}

function isFocusSessionRequest(value: unknown): value is FocusSessionRequest {
  return isRecord(value)
    && typeof value.subjectId === "number"
    && (value.goalId === null || typeof value.goalId === "number")
    && isValidFocusDuration(value.durationMinutes)
    && typeof value.startedAt === "string"
    && typeof value.completedAt === "string"
    && typeof value.requestKey === "string";
}

function restoreTimerState(): TimerRestoreResult {
  const serialized = window.sessionStorage.getItem(TIMER_STORAGE_KEY);
  if (!serialized) return { timer: defaultTimerState, error: null };

  try {
    const saved: unknown = JSON.parse(serialized);
    const focusDurationMinutes = isRecord(saved) && saved.focusDurationMinutes === undefined
      ? DEFAULT_FOCUS_MINUTES
      : isRecord(saved) ? saved.focusDurationMinutes : null;
    if (!isRecord(saved)
      || !isTimerMode(saved.mode)
      || !isValidFocusDuration(focusDurationMinutes)
      || typeof saved.remainingSeconds !== "number"
      || saved.remainingSeconds < 0
      || saved.remainingSeconds > modeDuration(saved.mode, focusDurationMinutes)
      || !(saved.startedAt === null || typeof saved.startedAt === "string")
      || !(saved.requestKey === null || typeof saved.requestKey === "string")
      || !(saved.subjectId === null || typeof saved.subjectId === "number")
      || !(saved.subjectName === null || typeof saved.subjectName === "string")
      || !(saved.goalId === null || typeof saved.goalId === "number")
      || !(saved.goalTitle === null || typeof saved.goalTitle === "string")
      || !(saved.pendingRequest === null
        || (isFocusSessionRequest(saved.pendingRequest)
          && saved.pendingRequest.durationMinutes === focusDurationMinutes))) {
      window.sessionStorage.removeItem(TIMER_STORAGE_KEY);
      return {
        timer: defaultTimerState,
        error: new Error("The saved timer state was invalid and has been cleared. Your completed sessions are unchanged."),
      };
    }

    return {
      timer: {
        mode: saved.mode,
        focusDurationMinutes,
        remainingSeconds: saved.remainingSeconds,
        startedAt: saved.startedAt,
        requestKey: saved.requestKey,
        subjectId: saved.subjectId,
        subjectName: saved.subjectName,
        goalId: saved.goalId,
        goalTitle: saved.goalTitle,
        pendingRequest: saved.pendingRequest,
      },
      error: null,
    };
  } catch (restoreError) {
    if (!(restoreError instanceof SyntaxError)) throw restoreError;
    window.sessionStorage.removeItem(TIMER_STORAGE_KEY);
    return {
      timer: defaultTimerState,
      error: new Error("The saved timer state could not be read and has been cleared. Your completed sessions are unchanged."),
    };
  }
}

function saveTimerState(timer: TimerState, onError?: (error: Error) => void): void {
  try {
    window.sessionStorage.setItem(TIMER_STORAGE_KEY, JSON.stringify(timer));
  } catch (storageError) {
    const message = storageError instanceof Error
      ? storageError.message
      : "Browser storage is unavailable.";
    onError?.(new Error(`The timer could not be saved for refresh: ${message}`));
  }
}

function localDateValue(date: Date): string {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

function dateForWeekStart(today: string): string {
  const date = new Date(`${today}T12:00:00`);
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7));
  return localDateValue(date);
}

function isoDateInBrowserZone(timestamp: string): string {
  return localDateValue(new Date(timestamp));
}

function readDailyGoal(): number {
  const saved = Number(window.sessionStorage.getItem(DAILY_GOAL_STORAGE_KEY));
  return Number.isInteger(saved) && saved >= 1 && saved <= 12
    ? saved
    : DEFAULT_DAILY_GOAL;
}

function modeDuration(mode: TimerMode, focusDurationMinutes: number): number {
  if (mode === "SHORT_BREAK") return SHORT_BREAK_MINUTES * 60;
  if (mode === "LONG_BREAK") return LONG_BREAK_MINUTES * 60;
  return focusDurationMinutes * 60;
}

function formatCountdown(seconds: number, showHours = seconds >= 60 * 60): string {
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  if (showHours) {
    const hours = Math.floor(minutes / 60);
    return `${String(hours).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`;
  }
  return `${String(minutes).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`;
}

function focusDurationLabel(minutes: number): string {
  if (minutes % 60 === 0) {
    const hours = minutes / 60;
    return `${hours} ${hours === 1 ? "hour" : "hours"}`;
  }
  return `${minutes} minutes`;
}

function modeLabel(mode: TimerMode): string {
  if (mode === "SHORT_BREAK") return "Short break";
  if (mode === "LONG_BREAK") return "Long break";
  return "Focus session";
}

async function loadFocusData(): Promise<FocusData> {
  await api.initializeSampleData();
  const today = localDateValue(new Date());
  const weekStart = dateForWeekStart(today);
  const [years, goals, sessions] = await Promise.all([
    api.listAcademicYears(),
    api.listGoals(),
    api.listFocusSessions({ startDate: weekStart, endDate: today }),
  ]);
  const yearSummaries: AcademicYearSummary[] = await Promise.all(
    years.map((year: AcademicYear) => api.getAcademicYearSummary(year.id)),
  );
  const subjects = yearSummaries.flatMap((summary) =>
    summary.semesters.flatMap((semester) => semester.subjects),
  );
  return { subjects, goals, sessions, today, weekStart };
}

export function FocusArenaPage() {
  const restored = useMemo(restoreTimerState, []);
  const [timer, setTimer] = useState<TimerState>(restored.timer);
  const [isRunning, setIsRunning] = useState(false);
  const [deadline, setDeadline] = useState<number | null>(null);
  const [data, setData] = useState<FocusData | null>(null);
  const [selectedSubjectId, setSelectedSubjectId] = useState<number | null>(restored.timer.subjectId);
  const [selectedGoalId, setSelectedGoalId] = useState<number | null>(restored.timer.goalId);
  const [dailyGoal, setDailyGoal] = useState(readDailyGoal);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(restored.error);
  const [success, setSuccess] = useState("");
  const [notificationsEnabled, setNotificationsEnabled] = useState(
    typeof Notification !== "undefined" && Notification.permission === "granted",
  );
  const inFlightKey = useRef<string | null>(null);
  const timerRef = useRef(timer);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const loaded = await loadFocusData();
      setData(loaded);
      setSelectedSubjectId((current) =>
        current && loaded.subjects.some((subject) => subject.id === current)
          ? current
          : loaded.subjects[0]?.id ?? null);
      setSelectedGoalId((current) =>
        current && loaded.goals.some((goal) => goal.id === current)
          ? current
          : null);
      setError(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    saveTimerState(timer, setError);
  }, [timer]);

  useEffect(() => {
    timerRef.current = timer;
  }, [timer]);

  useEffect(() => {
    try {
      window.sessionStorage.setItem(DAILY_GOAL_STORAGE_KEY, String(dailyGoal));
    } catch (storageError) {
      const message = storageError instanceof Error
        ? storageError.message
        : "Browser storage is unavailable.";
      setError(new Error(`The daily focus goal could not be saved: ${message}`));
    }
  }, [dailyGoal]);

  const todaySessions = useMemo(
    () => data?.sessions.filter((session) => isoDateInBrowserZone(session.completedAt) === data.today) || [],
    [data],
  );
  const weekMinutes = data?.sessions.reduce((sum, session) => sum + session.durationMinutes, 0) || 0;
  const todayMinutes = todaySessions.reduce((sum, session) => sum + session.durationMinutes, 0);
  const goalPercentage = dailyGoal > 0
    ? Math.min(100, Math.round((todaySessions.length / dailyGoal) * 100))
    : 0;
  const selectedSubject = data?.subjects.find((subject) => subject.id === selectedSubjectId);
  const activeSessionSubject = data?.subjects.find((subject) => subject.id === timer.subjectId);

  const showCompletionNotification = useCallback((subjectName: string) => {
    if (typeof Notification !== "undefined" && Notification.permission === "granted") {
      new Notification("Focus session complete", {
        body: `Nice work. Your ${subjectName} session has been saved.`,
        icon: "/favicon.svg",
      });
    }
  }, []);

  const submitCompletedSession = useCallback(async (request: FocusSessionRequest) => {
    if (inFlightKey.current === request.requestKey) return;
    inFlightKey.current = request.requestKey;
    setSaving(true);
    setError(null);

    try {
      const saved = await api.createFocusSession(request);
      setData((current) => current
        ? {
          ...current,
          sessions: current.sessions.some((session) => session.id === saved.id)
            ? current.sessions
            : [saved, ...current.sessions],
        }
        : current);

      const currentFocusDuration = timerRef.current.focusDurationMinutes;
      const nextMode = (todaySessions.length + 1) % 4 === 0
        ? "LONG_BREAK"
        : "SHORT_BREAK";
      const nextTimer: TimerState = {
        ...defaultTimerState,
        mode: nextMode,
        focusDurationMinutes: currentFocusDuration,
        remainingSeconds: modeDuration(nextMode, currentFocusDuration),
      };
      saveTimerState(nextTimer, setError);
      setTimer(nextTimer);
      setSuccess(`Your ${request.durationMinutes}-minute ${saved.subjectName} session was saved.`);
      showCompletionNotification(saved.subjectName);
      await refresh();
    } catch (saveError) {
      setError(saveError);
    } finally {
      setSaving(false);
      inFlightKey.current = null;
    }
  }, [refresh, showCompletionNotification, todaySessions.length]);

  const submitSessionRef = useRef(submitCompletedSession);
  useEffect(() => {
    submitSessionRef.current = submitCompletedSession;
  }, [submitCompletedSession]);

  useEffect(() => {
    if (timer.pendingRequest) {
      void submitCompletedSession(timer.pendingRequest);
    }
  }, [submitCompletedSession, timer.pendingRequest]);

  useEffect(() => {
    if (!isRunning || deadline === null) return;
    let phaseCompleted = false;
    const tick = () => {
      const remainingSeconds = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
      setTimer((current) => current.remainingSeconds === remainingSeconds
        ? current
        : { ...current, remainingSeconds });
      if (remainingSeconds > 0 || phaseCompleted) return;

      phaseCompleted = true;
      setIsRunning(false);
      setDeadline(null);
      const currentTimer = timerRef.current;
      if (currentTimer.mode !== "FOCUS") {
        const nextTimer: TimerState = {
          ...defaultTimerState,
          focusDurationMinutes: currentTimer.focusDurationMinutes,
          remainingSeconds: modeDuration("FOCUS", currentTimer.focusDurationMinutes),
        };
        saveTimerState(nextTimer, setError);
        setTimer(nextTimer);
        setSuccess("Break complete. You’re ready for another focused session.");
        return;
      }

      if (!currentTimer.subjectId || !currentTimer.startedAt || !currentTimer.requestKey) {
        setError(new Error("The focus session details are missing. Reset the timer and choose a subject."));
        return;
      }
      const request: FocusSessionRequest = {
        subjectId: currentTimer.subjectId,
        goalId: currentTimer.goalId,
        durationMinutes: currentTimer.focusDurationMinutes,
        startedAt: currentTimer.startedAt,
        completedAt: new Date().toISOString(),
        requestKey: currentTimer.requestKey,
      };
      const pendingTimer = { ...currentTimer, remainingSeconds: 0, pendingRequest: request };
      saveTimerState(pendingTimer, setError);
      setTimer(pendingTimer);
      setSuccess("");
      void submitSessionRef.current(request);
    };
    const interval = window.setInterval(tick, 1000);
    tick();
    return () => window.clearInterval(interval);
  }, [deadline, isRunning]);

  const start = () => {
    if (saving || timer.pendingRequest) return;
    let nextTimer = timer;
    if (timer.mode === "FOCUS" && timer.startedAt === null) {
      if (!selectedSubject || !data) {
        setError(new Error("Add an academic subject before starting a focus session."));
        return;
      }
      nextTimer = {
        ...timer,
        startedAt: new Date().toISOString(),
        requestKey: crypto.randomUUID(),
        subjectId: selectedSubject.id,
        subjectName: selectedSubject.name,
        goalId: selectedGoalId,
        goalTitle: data.goals.find((goal) => goal.id === selectedGoalId)?.title || null,
      };
      setTimer(nextTimer);
    }
    setError(null);
    setSuccess("");
    setDeadline(Date.now() + nextTimer.remainingSeconds * 1000);
    setIsRunning(true);
  };

  const pause = () => {
    if (deadline !== null) {
      const remainingSeconds = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
      setTimer((current) => ({ ...current, remainingSeconds }));
    }
    setDeadline(null);
    setIsRunning(false);
  };

  const reset = () => {
    if (timer.pendingRequest || saving) return;
    setDeadline(null);
    setIsRunning(false);
    const resetTimer: TimerState = {
      ...defaultTimerState,
      mode: timer.mode,
      focusDurationMinutes: timer.focusDurationMinutes,
      remainingSeconds: modeDuration(timer.mode, timer.focusDurationMinutes),
    };
    saveTimerState(resetTimer, setError);
    setTimer(resetTimer);
    setError(null);
    setSuccess("");
  };

  const enableNotifications = async () => {
    if (typeof Notification === "undefined") {
      setError(new Error("This browser does not support desktop notifications."));
      return;
    }
    const permission = await Notification.requestPermission();
    setNotificationsEnabled(permission === "granted");
    if (permission !== "granted") {
      setError(new Error("Notification permission was not granted. Completed sessions will still be saved."));
    } else {
      setError(null);
    }
  };

  const selectMode = (mode: TimerMode) => {
    if (isRunning || timer.startedAt !== null || timer.pendingRequest || saving) return;
    const nextTimer: TimerState = {
      ...defaultTimerState,
      mode,
      focusDurationMinutes: timer.focusDurationMinutes,
      remainingSeconds: modeDuration(mode, timer.focusDurationMinutes),
    };
    setTimer(nextTimer);
    setError(null);
    setSuccess("");
  };

  const selectFocusDuration = (focusDurationMinutes: number) => {
    if (!isValidFocusDuration(focusDurationMinutes) || !canEditSession || timer.mode !== "FOCUS") return;
    const nextTimer: TimerState = {
      ...timer,
      focusDurationMinutes,
      remainingSeconds: focusDurationMinutes * 60,
    };
    setTimer(nextTimer);
    setError(null);
    setSuccess("");
  };

  if (loading && !data) return <LoadingState label="Preparing your Focus Arena…" />;
  if (error && !data) {
    return (
      <>
        <PageHeader eyebrow="SMART STUDY COMPANION" title="Focus Arena" />
        <ErrorState error={error} onRetry={() => void refresh()} />
      </>
    );
  }

  const activeDurationSeconds = modeDuration(timer.mode, timer.focusDurationMinutes);
  const timerProgress = Math.min(1, Math.max(0, (activeDurationSeconds - timer.remainingSeconds) / activeDurationSeconds));
  const strokeOffset = CIRCUMFERENCE * (1 - timerProgress);
  const currentSubjectLabel = timer.startedAt
    ? timer.subjectName || activeSessionSubject?.name || "Selected subject"
    : selectedSubject?.name || "Choose a subject";
  const canEditSession = !isRunning && timer.startedAt === null && !timer.pendingRequest && !saving;

  return (
    <>
      <PageHeader
        eyebrow="SMART STUDY COMPANION"
        title="Focus Arena"
        description="Settle into one subject, choose your pace, and give your full attention to the work."
        action={(
          <button
            type="button"
            className={`button ${notificationsEnabled ? "button-secondary" : "button-ghost"}`}
            onClick={() => void enableNotifications()}
          >
            <Bell size={16} /> {notificationsEnabled ? "Notifications on" : "Enable notifications"}
          </button>
        )}
      />
      {error && <ErrorState error={error} onRetry={timer.pendingRequest ? () => void submitCompletedSession(timer.pendingRequest!) : undefined} />}
      {success && <div className="focus-success" role="status"><CheckCircle2 size={17} />{success}</div>}

      <section className="focus-arena-layout">
        <div className="focus-timer-card panel">
          <div className="focus-mode-tabs" role="group" aria-label="Timer session type">
            {(["FOCUS", "SHORT_BREAK", "LONG_BREAK"] as const).map((mode) => (
              <button
                type="button"
                key={mode}
                className={`focus-mode-tab ${timer.mode === mode ? "focus-mode-active" : ""}`}
                disabled={!canEditSession}
                onClick={() => selectMode(mode)}
              >
                {modeLabel(mode)}
              </button>
            ))}
          </div>
          <div
            className={`focus-clock focus-clock-${timer.mode.toLowerCase()}`}
            role="timer"
            aria-label={`${modeLabel(timer.mode)}, ${formatCountdown(timer.remainingSeconds, timer.mode === "FOCUS" && timer.focusDurationMinutes >= 60)} remaining`}
          >
            <svg viewBox="0 0 280 280" className="focus-clock-svg" aria-hidden="true">
              <circle className="focus-clock-track" cx="140" cy="140" r="126" />
              <circle
                className="focus-clock-progress"
                cx="140"
                cy="140"
                r="126"
                strokeDasharray={CIRCUMFERENCE}
                strokeDashoffset={strokeOffset}
              />
            </svg>
            <div className="focus-clock-content">
              <span className="focus-clock-label">{modeLabel(timer.mode)}</span>
              <strong>{formatCountdown(timer.remainingSeconds, timer.mode === "FOCUS" && timer.focusDurationMinutes >= 60)}</strong>
              <span className="focus-clock-subject">{currentSubjectLabel}</span>
            </div>
          </div>
          <div className="focus-timer-controls">
            {isRunning ? (
              <button type="button" className="button button-primary focus-main-control" onClick={pause}>
                <Pause size={17} /> Pause
              </button>
            ) : (
              <button
                type="button"
                className="button button-primary focus-main-control"
                onClick={start}
                disabled={saving || Boolean(timer.pendingRequest) || (timer.mode === "FOCUS" && !selectedSubject)}
              >
                <Play size={17} /> {timer.startedAt ? "Resume focus" : timer.mode === "FOCUS" ? "Start focusing" : "Start break"}
              </button>
            )}
            <button
              type="button"
              className="button button-secondary focus-reset-button"
              onClick={reset}
              disabled={saving || Boolean(timer.pendingRequest)}
              aria-label="Reset timer"
            >
              <RotateCcw size={16} /> Reset
            </button>
          </div>
          <p className="focus-timer-hint">
            {timer.pendingRequest
              ? "Saving your completed session…"
              : timer.startedAt
                ? "Your timer is saved in this tab. Refreshing will safely pause it."
                : timer.mode === "FOCUS"
                  ? `One session is ${focusDurationLabel(timer.focusDurationMinutes)} of focused study.`
                  : "Take a real pause before your next study sprint."}
          </p>
        </div>

        <aside className="focus-side-column">
          <section className="panel focus-session-settings">
            <div className="panel-heading">
              <div><h2>Session setup</h2><p>Keep this sprint intentional</p></div>
              <span className="panel-icon"><BookOpen size={17} /></span>
            </div>
            <label className="field">
              <span>Study subject</span>
              <select
                value={selectedSubjectId ?? ""}
                disabled={!canEditSession}
                onChange={(event) => setSelectedSubjectId(event.target.value ? Number(event.target.value) : null)}
              >
                {data?.subjects.length
                  ? data.subjects.map((subject) => (
                    <option key={subject.id} value={subject.id}>
                      {subject.code ? `${subject.code} · ` : ""}{subject.name}
                    </option>
                  ))
                  : <option value="">No subjects available</option>}
              </select>
            </label>
            <label className="field">
              <span>Focus duration</span>
              <select
                aria-label="Focus duration"
                value={timer.focusDurationMinutes}
                disabled={!canEditSession || timer.mode !== "FOCUS"}
                onChange={(event) => selectFocusDuration(Number(event.target.value))}
              >
                {Array.from(
                  { length: (MAX_FOCUS_MINUTES - MIN_FOCUS_MINUTES) / FOCUS_MINUTE_STEP + 1 },
                  (_, index) => MIN_FOCUS_MINUTES + index * FOCUS_MINUTE_STEP,
                ).map((minutes) => (
                  <option key={minutes} value={minutes}>{focusDurationLabel(minutes)}</option>
                ))}
              </select>
              <small>{focusDurationLabel(timer.focusDurationMinutes)} selected</small>
            </label>
            <label className="field">
              <span>Link progress to a goal <small>(optional)</small></span>
              <select
                value={selectedGoalId ?? ""}
                disabled={!canEditSession}
                onChange={(event) => setSelectedGoalId(event.target.value ? Number(event.target.value) : null)}
              >
                <option value="">No goal selected</option>
                {data?.goals.filter((goal) => goal.status !== "CANCELLED").map((goal) => (
                  <option key={goal.id} value={goal.id}>{goal.title}</option>
                ))}
              </select>
            </label>
            {!data?.subjects.length && (
              <div className="focus-no-subject">
                <p>Add a subject in Academic progress to begin tracking focus sessions.</p>
                <Link to="/academic" className="text-link">Open academic progress <ArrowUpRight size={14} /></Link>
              </div>
            )}
            <div className="focus-session-note"><Sparkles size={15} /><span>Completed focus sessions appear in study history and count toward the selected goal.</span></div>
          </section>

          <section className="panel focus-daily-goal">
            <div className="panel-heading">
              <div><h2>Today’s focus goal</h2><p>Build a steady, achievable rhythm</p></div>
              <span className="panel-icon"><Target size={17} /></span>
            </div>
            <div className="focus-goal-progress-copy">
              <strong>{todaySessions.length} <span>of {dailyGoal} sessions</span></strong>
              <span>{goalPercentage}%</span>
            </div>
            <div
              className="focus-goal-track"
              role="progressbar"
              aria-label="Daily focus goal completion"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={goalPercentage}
            >
              <span style={{ width: `${goalPercentage}%` }} />
            </div>
            <div className="focus-goal-footer">
              <span>{formatMinutes(todayMinutes)} focused today</span>
              <label className="focus-goal-selector">
                <span>Daily target</span>
                <select
                  aria-label="Daily focus session goal"
                  value={dailyGoal}
                  onChange={(event) => setDailyGoal(Number(event.target.value))}
                >
                  {[2, 3, 4, 5, 6, 8, 10, 12].map((count) => (
                    <option key={count} value={count}>{count} sessions</option>
                  ))}
                </select>
              </label>
            </div>
          </section>
        </aside>
      </section>

      <section className="focus-stat-grid" aria-label="Focus statistics">
        <article className="focus-stat-card">
          <span className="focus-stat-icon focus-stat-violet"><CheckCircle2 size={18} /></span>
          <div><span>Completed today</span><strong>{todaySessions.length}</strong></div>
        </article>
        <article className="focus-stat-card">
          <span className="focus-stat-icon focus-stat-teal"><Clock3 size={18} /></span>
          <div><span>Focus time today</span><strong>{formatMinutes(todayMinutes)}</strong></div>
        </article>
        <article className="focus-stat-card">
          <span className="focus-stat-icon focus-stat-blue"><Sparkles size={18} /></span>
          <div><span>This week</span><strong>{formatMinutes(weekMinutes)}</strong></div>
        </article>
        <article className="focus-stat-card">
          <span className="focus-stat-icon focus-stat-amber"><Target size={18} /></span>
          <div><span>Daily goal</span><strong>{goalPercentage}%</strong></div>
        </article>
      </section>

      <section className="dashboard-grid focus-lower-grid">
        <div className="panel focus-week-panel">
          <div className="panel-heading">
            <div><h2>Your focus week</h2><p>{data ? `${formatDate(data.weekStart)} – ${formatDate(data.today)}` : "Completed sessions this week"}</p></div>
            <span className="panel-icon"><TimerReset size={17} /></span>
          </div>
          <div className="focus-week-bars" aria-label="Weekly focus minutes">
            {Array.from({ length: 7 }, (_, index) => {
              const date = new Date(`${data?.weekStart || localDateValue(new Date())}T12:00:00`);
              date.setDate(date.getDate() + index);
              const dateValue = localDateValue(date);
              const minutes = data?.sessions
                .filter((session) => isoDateInBrowserZone(session.completedAt) === dateValue)
                .reduce((sum, session) => sum + session.durationMinutes, 0) || 0;
              const maxMinutes = Math.max(100, ...((data?.sessions || []).map((session) => session.durationMinutes)));
              return (
                <div className="focus-week-day" key={dateValue}>
                  <span>{new Intl.DateTimeFormat(undefined, { weekday: "short" }).format(date)}</span>
                  <div className="focus-week-bar-track" title={`${minutes} minutes`}>
                    <span style={{ height: `${minutes === 0 ? 0 : Math.max(8, (minutes / maxMinutes) * 100)}%` }} />
                  </div>
                  <small>{minutes ? `${minutes}m` : "—"}</small>
                </div>
              );
            })}
          </div>
        </div>
        <div className="panel focus-recent-panel">
          <div className="panel-heading">
            <div><h2>Recent focus sessions</h2><p>Work saved from completed focus rounds</p></div>
            <Link to="/history" className="text-link">Study history <ArrowUpRight size={14} /></Link>
          </div>
          {!data?.sessions.length ? (
            <EmptyState
              icon={<Clock3 size={22} />}
              title="Your first focus round is waiting"
              description="Finish a focus session and it will appear here."
            />
          ) : (
            <div className="focus-recent-list">
              {data.sessions.slice(0, 5).map((session) => (
                <article className="focus-recent-row" key={session.id}>
                  <span className="focus-recent-check"><CheckCircle2 size={16} /></span>
                  <span className="focus-recent-copy">
                    <strong>{session.subjectName}</strong>
                    <small>{session.goalTitle ? `${session.goalTitle} · ` : ""}{formatDate(isoDateInBrowserZone(session.completedAt))}</small>
                  </span>
                  <span className="focus-recent-duration">{formatMinutes(session.durationMinutes)}</span>
                </article>
              ))}
            </div>
          )}
        </div>
      </section>
      <p className="focus-footer-note">Only completed focus sessions are saved. Paused, reset, or abandoned focus rounds are not counted.</p>
    </>
  );
}
