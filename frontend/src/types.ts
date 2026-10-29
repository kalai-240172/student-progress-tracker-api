export type RecordCategory = "ACADEMIC" | "CERTIFICATE" | "PROJECT" | "ACHIEVEMENT";
export type RecordStatus = "IN_PROGRESS" | "COMPLETED" | "PLANNED";
export type GoalCategory = "STUDY" | "PROJECT" | "CAREER" | "PERSONAL";
export type GoalStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
export type Weekday =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export interface StudentRecord {
  id: number;
  title: string;
  description: string | null;
  category: RecordCategory;
  institution: string | null;
  startDate: string | null;
  endDate: string | null;
  status: RecordStatus;
  createdAt: string;
  updatedAt: string;
}

export interface RecordRequest {
  title: string;
  description: string | null;
  category: RecordCategory;
  institution: string | null;
  startDate: string | null;
  endDate: string | null;
  status: RecordStatus;
}

export interface Goal {
  id: number;
  title: string;
  description: string | null;
  category: GoalCategory;
  targetDate: string | null;
  targetDurationMinutes: number;
  preferredWeekdays: Weekday[];
  status: GoalStatus;
  createdAt: string;
  updatedAt: string;
}

export interface GoalRequest {
  title: string;
  description: string | null;
  category: GoalCategory;
  targetDate: string | null;
  targetDurationMinutes: number;
  preferredWeekdays: Weekday[];
  status: GoalStatus;
}

export interface ProgressLog {
  id: number;
  goalId: number;
  activityDate: string;
  timeSpentMinutes: number;
  summary: string;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProgressLogRequest {
  activityDate: string;
  timeSpentMinutes: number;
  summary: string;
  notes: string | null;
}

export interface ProgressSummary {
  goalId: number;
  goalTitle: string;
  targetDurationMinutes: number;
  totalRecordedMinutes: number;
  remainingMinutes: number;
  progressPercentage: number;
  totalLogCount: number;
  currentWeekMinutes: number;
  currentMonthMinutes: number;
  mostRecentActivityDate: string | null;
}

export interface RegistrationRequest {
  name: string;
  email: string;
  password: string;
}

export interface RegistrationResponse {
  id: number;
  name: string;
  email: string;
  createdAt: string;
}

export interface ApiErrorBody {
  status?: number;
  error?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
}
