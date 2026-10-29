import type { DailyTask } from "../types";

export type CalendarView = "month" | "week";

export interface DateRange {
  startDate: string;
  endDate: string;
}

export interface DayHistory {
  date: string;
  tasks: DailyTask[];
  taskCount: number;
  completedCount: number;
  plannedMinutes: number;
  actualMinutes: number;
  completionRate: number;
}

export function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function parseLocalDate(value: string): Date {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

export function addDays(date: Date, count: number): Date {
  const result = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  result.setDate(result.getDate() + count);
  return result;
}

export function calendarRange(anchor: Date, view: CalendarView): DateRange {
  if (view === "week") {
    const start = addDays(anchor, -((anchor.getDay() + 6) % 7));
    return { startDate: formatLocalDate(start), endDate: formatLocalDate(addDays(start, 6)) };
  }
  const monthStart = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  const gridStart = addDays(monthStart, -((monthStart.getDay() + 6) % 7));
  return { startDate: formatLocalDate(gridStart), endDate: formatLocalDate(addDays(gridStart, 41)) };
}

export function calendarDates(anchor: Date, view: CalendarView): Date[] {
  const range = calendarRange(anchor, view);
  const start = parseLocalDate(range.startDate);
  return Array.from({ length: view === "month" ? 42 : 7 }, (_, index) => addDays(start, index));
}

export function navigateCalendar(anchor: Date, view: CalendarView, direction: -1 | 1): Date {
  if (view === "week") return addDays(anchor, direction * 7);
  return new Date(anchor.getFullYear(), anchor.getMonth() + direction, 1);
}

export function groupTaskHistory(tasks: DailyTask[]): DayHistory[] {
  const byDate = new Map<string, DailyTask[]>();
  for (const task of tasks) {
    const group = byDate.get(task.plannedDate) || [];
    group.push(task);
    byDate.set(task.plannedDate, group);
  }
  return [...byDate.entries()]
    .sort(([left], [right]) => right.localeCompare(left))
    .map(([date, dayTasks]) => {
      const completedCount = dayTasks.filter((task) => task.status === "COMPLETED").length;
      return {
        date,
        tasks: dayTasks,
        taskCount: dayTasks.length,
        completedCount,
        plannedMinutes: dayTasks.reduce((sum, task) => sum + task.estimatedDurationMinutes, 0),
        actualMinutes: dayTasks.reduce((sum, task) => sum + (task.actualDurationMinutes ?? 0), 0),
        completionRate: dayTasks.length === 0 ? 0 : (completedCount / dayTasks.length) * 100,
      };
    });
}

export function identifyOverdueTasks(tasks: DailyTask[], today: string): DailyTask[] {
  return tasks.filter((task) => task.plannedDate < today && task.status !== "COMPLETED");
}
