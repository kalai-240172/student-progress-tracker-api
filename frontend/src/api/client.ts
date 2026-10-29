import type {
  ApiErrorBody,
  Goal,
  GoalRequest,
  ProgressLog,
  ProgressLogRequest,
  ProgressSummary,
  RecordRequest,
  RegistrationRequest,
  RegistrationResponse,
  StudentRecord,
} from "../types";

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/+$/, "");
let basicAuthorization: string | null = null;
let csrfToken: { headerName: string; token: string } | null = null;

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

export function setCredentials(email: string, password: string): void {
  basicAuthorization = `Basic ${btoa(unescape(encodeURIComponent(`${email}:${password}`)))}`;
  csrfToken = null;
}

export function clearCredentials(): void {
  basicAuthorization = null;
  csrfToken = null;
}

export function hasCredentials(): boolean {
  return basicAuthorization !== null;
}

function requestUrl(path: string): URL {
  const url = new URL(`${apiBaseUrl}${path}`, window.location.origin);
  if (basicAuthorization && url.origin !== window.location.origin && apiBaseUrl.startsWith("/")) {
    throw new ApiError(0, "The API URL must use the current origin when credentials are active.");
  }
  return url;
}

async function readError(response: Response): Promise<ApiError> {
  let body: ApiErrorBody = {};
  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    // Error responses without JSON use a safe status-specific message below.
  }

  const fallback: Record<number, string> = {
    400: "Please review the highlighted fields.",
    401: "Your login is no longer valid. Please sign in again.",
    403: "This action was blocked. Refresh the page and try again.",
    404: "The requested item could not be found.",
    409: "An account with this email already exists.",
  };
  return new ApiError(
    response.status,
    body.message || fallback[response.status] || "The request could not be completed.",
    body.fieldErrors || {},
  );
}

async function obtainCsrfToken(): Promise<void> {
  if (csrfToken) return;
  const response = await fetch(requestUrl("/csrf"), {
    method: "GET",
    headers: basicAuthorization ? { Authorization: basicAuthorization } : undefined,
    credentials: "include",
  });
  if (!response.ok) throw await readError(response);
  csrfToken = (await response.json()) as { headerName: string; token: string };
}

async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; skipCsrf?: boolean } = {},
  csrfRetry = true,
): Promise<T> {
  const method = options.method || "GET";
  const headers = new Headers();
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  if (basicAuthorization) headers.set("Authorization", basicAuthorization);

  if (!options.skipCsrf && !["GET", "HEAD", "OPTIONS"].includes(method)) {
    await obtainCsrfToken();
    if (csrfToken) headers.set(csrfToken.headerName, csrfToken.token);
  }

  let response: Response;
  try {
    response = await fetch(requestUrl(path), {
      method,
      headers,
      credentials: "include",
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(0, "Unable to reach the server. Check your connection and backend status.");
  }

  if (response.status === 401) {
    clearCredentials();
    window.dispatchEvent(new Event("studyflow:unauthorized"));
  }
  if (response.status === 403 && csrfRetry && !["GET", "HEAD", "OPTIONS"].includes(method)) {
    csrfToken = null;
    return request<T>(path, options, false);
  }
  if (!response.ok) throw await readError(response);
  if (response.status === 204) return undefined as T;
  if (response.status === 201 || response.status === 200) {
    return (await response.json()) as T;
  }
  return undefined as T;
}

export const api = {
  register: (body: RegistrationRequest) =>
    request<RegistrationResponse>("/auth/register", {
      method: "POST",
      body,
      skipCsrf: true,
    }),
  listRecords: () => request<StudentRecord[]>("/records"),
  getRecord: (id: number) => request<StudentRecord>(`/records/${id}`),
  createRecord: (body: RecordRequest) =>
    request<StudentRecord>("/records", { method: "POST", body }),
  updateRecord: (id: number, body: RecordRequest) =>
    request<StudentRecord>(`/records/${id}`, { method: "PUT", body }),
  deleteRecord: (id: number) =>
    request<void>(`/records/${id}`, { method: "DELETE" }),
  listGoals: () => request<Goal[]>("/goals"),
  getGoal: (id: number) => request<Goal>(`/goals/${id}`),
  createGoal: (body: GoalRequest) => request<Goal>("/goals", { method: "POST", body }),
  updateGoal: (id: number, body: GoalRequest) =>
    request<Goal>(`/goals/${id}`, { method: "PUT", body }),
  deleteGoal: (id: number) => request<void>(`/goals/${id}`, { method: "DELETE" }),
  getProgress: (id: number) => request<ProgressSummary>(`/goals/${id}/progress`),
  listLogs: (goalId: number) => request<ProgressLog[]>(`/goals/${goalId}/logs`),
  getLog: (goalId: number, logId: number) =>
    request<ProgressLog>(`/goals/${goalId}/logs/${logId}`),
  createLog: (goalId: number, body: ProgressLogRequest) =>
    request<ProgressLog>(`/goals/${goalId}/logs`, { method: "POST", body }),
  updateLog: (goalId: number, logId: number, body: ProgressLogRequest) =>
    request<ProgressLog>(`/goals/${goalId}/logs/${logId}`, { method: "PUT", body }),
  deleteLog: (goalId: number, logId: number) =>
    request<void>(`/goals/${goalId}/logs/${logId}`, { method: "DELETE" }),
};
