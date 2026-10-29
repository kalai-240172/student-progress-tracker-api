import type { DailyTask } from "../types";
import { formatLocalDate, parseLocalDate } from "../tasks/dateUtils";

export type ReportPeriod = "week" | "month" | "custom";

export interface ReportRange {
  startDate: string;
  endDate: string;
}

export interface DailyReportPoint {
  date: string;
  plannedMinutes: number;
  actualMinutes: number;
  completedTasks: number;
  totalTasks: number;
}

export interface ReportMetrics {
  totalTasks: number;
  completedTasks: number;
  completionRate: number;
  plannedMinutes: number;
  actualMinutes: number;
  days: DailyReportPoint[];
}

export function reportRange(anchor: Date, period: Exclude<ReportPeriod, "custom">, today: Date): ReportRange {
  const anchorDate = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate());
  const todayDate = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const start = period === "week"
    ? new Date(anchorDate.getFullYear(), anchorDate.getMonth(), anchorDate.getDate() - ((anchorDate.getDay() + 6) % 7))
    : new Date(anchorDate.getFullYear(), anchorDate.getMonth(), 1);
  const periodEnd = period === "week"
    ? new Date(start.getFullYear(), start.getMonth(), start.getDate() + 6)
    : new Date(anchorDate.getFullYear(), anchorDate.getMonth() + 1, 0);
  const end = periodEnd > todayDate ? todayDate : periodEnd;
  return { startDate: formatLocalDate(start), endDate: formatLocalDate(end) };
}

export function previousReportAnchor(anchor: Date, period: Exclude<ReportPeriod, "custom">): Date {
  return period === "week"
    ? new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate() - 7)
    : new Date(anchor.getFullYear(), anchor.getMonth() - 1, 1);
}

export function nextReportAnchor(anchor: Date, period: Exclude<ReportPeriod, "custom">): Date {
  return period === "week"
    ? new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate() + 7)
    : new Date(anchor.getFullYear(), anchor.getMonth() + 1, 1);
}

export function buildReportMetrics(tasks: DailyTask[], range: ReportRange, today: string): ReportMetrics {
  const inRange = [...new Map(tasks
    .filter((task) =>
      task.plannedDate >= range.startDate && task.plannedDate <= range.endDate && task.plannedDate <= today)
    .map((task) => [task.id, task])).values()];
  const byDate = new Map<string, DailyTask[]>();
  for (const task of inRange) {
    const sameDayTasks = byDate.get(task.plannedDate) || [];
    sameDayTasks.push(task);
    byDate.set(task.plannedDate, sameDayTasks);
  }
  const days = [...byDate.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([date, dateTasks]) => ({
      date,
      plannedMinutes: dateTasks.reduce((sum, task) => sum + task.estimatedDurationMinutes, 0),
      actualMinutes: dateTasks.reduce((sum, task) => sum + (task.actualDurationMinutes ?? 0), 0),
      completedTasks: dateTasks.filter((task) => task.status === "COMPLETED").length,
      totalTasks: dateTasks.length,
    }));
  const totalTasks = inRange.length;
  const completedTasks = inRange.filter((task) => task.status === "COMPLETED").length;
  return {
    totalTasks,
    completedTasks,
    completionRate: totalTasks === 0 ? 0 : (completedTasks / totalTasks) * 100,
    plannedMinutes: inRange.reduce((sum, task) => sum + task.estimatedDurationMinutes, 0),
    actualMinutes: inRange.reduce((sum, task) => sum + (task.actualDurationMinutes ?? 0), 0),
    days,
  };
}

export function defaultCustomRange(today: Date): ReportRange {
  const endDate = formatLocalDate(today);
  const start = parseLocalDate(endDate);
  start.setDate(start.getDate() - 6);
  return { startDate: formatLocalDate(start), endDate };
}
