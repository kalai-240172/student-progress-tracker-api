# Studyflow frontend

Responsive React + TypeScript frontend for the existing Personal Student Record & Progress Tracking API. The frontend consumes the real Spring Boot registration, records, goals, progress-log, task-planner, summary, and CSRF endpoints; it does not provide mock data or an API.

The shared interface uses a dark navy and purple design system across authentication and all authenticated routes. Responsive layout rules, keyboard focus states, and reduced-motion preferences are shared through `src/styles.css`; chart colours follow the same lavender palette.

## Prerequisites

- Windows 10/11 with PowerShell
- Node.js 22.12+ or 24 LTS and npm (Node.js 24 LTS recommended)
- Java 17 for the Spring Boot backend

Check Node and npm versions:

```powershell
node --version
npm --version
```

On Windows, install the Node.js LTS prerequisite with:

```powershell
winget install --id OpenJS.NodeJS.LTS --exact
```

Open a new PowerShell window after installation.

## Install and run

From the repository root, start the backend in its own PowerShell window:

```powershell
.\mvnw.cmd spring-boot:run
```

In another PowerShell window, install frontend dependencies and start Vite:

```powershell
Set-Location .\frontend
npm install
npm run dev
```

Open the local URL printed by Vite (normally `http://localhost:5173`). The Vite development server proxies `/api` requests to `http://localhost:8080` by default, so the browser uses one origin for API calls, the server session cookie, and CSRF protection.

To select another backend for local development, create `frontend\.env.local`:

```dotenv
VITE_BACKEND_URL=http://localhost:8080
VITE_API_BASE_URL=/api
```

Change `VITE_BACKEND_URL` to the backend server URL, restart Vite, and keep `VITE_API_BASE_URL=/api` so calls use the same-origin proxy. Do not set a wildcard CORS origin or expose credentials to unrelated origins. For a production deployment, serve the frontend and route `/api` through a same-origin reverse proxy; the existing backend does not enable cross-origin browser requests.

Build and test the frontend:

```powershell
Set-Location .\frontend
npm run build
npm test
```

To preview the production build locally:

```powershell
npm run preview
```

## Accounts and authentication

Choose **Create an account** on the sign-in page. Registration uses `POST /api/auth/register`; names, email, and password are validated to match the backend. Then sign in with the registered email and password.

The browser signs in through `POST /api/auth/login` and uses an HttpOnly, SameSite=Lax server session cookie. The cookie remains valid for up to 12 hours, subject to the server's 12-hour inactivity timeout. On application load, `GET /api/auth/session` restores a valid session; `POST /api/auth/logout` invalidates it. The browser never stores passwords or session credentials in local or session storage. Direct API clients may continue using HTTP Basic authentication for backward compatibility. Use HTTPS in deployment; `SESSION_COOKIE_SECURE` defaults to `true` and may be set to `false` only for local environments that cannot use secure cookies.

Login and other write requests first fetch `GET /api/csrf`, then send the backend-provided header and token. If a token expires, the client refreshes it and retries a write once. Invalid or expired sessions return students to sign-in; temporary session-verification errors show a retry option.

## Features and API routes

- Overview: `POST /api/sample-data/initialize`, `GET /api/records`, `GET /api/tasks`, `GET /api/tasks/today-summary`, `GET /api/goals`, `GET /api/goals/{id}/progress`, and `GET /api/goals/{goalId}/logs` power the saved-record count, task metrics, today's study time, goal completion, weekly/monthly time, recent sessions, and upcoming tasks. Sample data is inserted once per account only where a module is empty; dashboard statistics use API data.
- Daily tasks: `/tasks` calls `GET /api/tasks`, `GET /api/tasks/today-summary`, and the task create, update, status, actual-duration, and delete endpoints. It supports today, all tasks, and a selected date.
- Focus Arena: `/focus` loads existing academic subjects and goals, then uses `POST /api/focus-sessions` and an inclusive date range `GET /api/focus-sessions` for saved completed sessions. Focus duration is selectable from 5 to 120 minutes in 5-minute steps; short and long breaks remain fixed at 5 and 15 minutes. Focus sessions create completed daily tasks with the selected duration for study history/reports/streaks and progress logs of the same duration for an optionally selected goal. The timer restores paused after refresh; a request key prevents duplicate completion writes.
- Calendar: `/calendar` requests the visible month or Monday-to-Sunday week using the inclusive task date-range API and displays per-day task/completion counts.
- Study history: `/history` uses inclusive start/end filters, displays per-day task/time summaries and a planned-versus-recorded-time chart, and fetches owner-scoped overdue tasks and study streaks. Rescheduling updates the existing task rather than creating a replacement.
- Reports: `/reports` reuses the owner-scoped task date-range API for weekly, monthly, and custom reports. Completion rate uses completed tasks divided by all scheduled tasks in-range; actual time includes recorded durations only, and future task dates are excluded.
- Academic progress: `/academic` manages owner-scoped academic years, semesters, and subjects through the typed `/api/academic-years`, `/api/semesters`, and `/api/subjects` APIs. Summaries use the official R-2023 CBCCS grade-point mapping and backend-calculated eligible-course GPA/CGPA; marks do not automatically determine grades.
- CSV exports: `/exports` downloads owner-scoped study history and task data with optional inclusive date ranges, and academic results filtered by academic-year and/or semester IDs. Downloads use authenticated `text/csv` responses as browser files; empty exports contain headers, missing values stay blank, and formula-like text is escaped safely.
- Student records: `GET/POST /api/records`, `GET/PUT/DELETE /api/records/{id}`.
- Goals: `GET/POST /api/goals`, `GET/PUT/DELETE /api/goals/{id}`.
- Focus sessions: `POST /api/focus-sessions` and `GET /api/focus-sessions?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`.
- Goal detail: `GET /api/goals/{goalId}/progress`, `GET/POST /api/goals/{goalId}/logs`, and `GET/PUT/DELETE /api/goals/{goalId}/logs/{logId}`.
- Account access: `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/session`, and `POST /api/auth/logout`; browser calls use session cookies and CSRF protection. HTTP Basic remains available for direct API clients.

Dashboard activity uses the backend summary's ISO Monday-to-Monday week and calendar-month boundaries. Total recorded minutes are summed from each goal's saved summary, and recent activity comes from persisted logs. Goal percentage represents time against a duration target, not learning quality. Initialization fills each relevant module to five records where needed, including accounts initialized by an earlier release, while leaving existing user records unchanged.

## Troubleshooting

- If the app reports that it cannot reach the server, start Spring Boot separately and verify `http://localhost:8080/api/health`.
- If the backend runs on a different port, update `VITE_BACKEND_URL` in `.env.local` and restart Vite.
- A `401` means credentials were rejected or cleared; sign in again.
- A `403` on a write can indicate an expired CSRF token; the client refreshes once. If it persists, reload and sign in again.
- A `404` for another user's record, goal, log, or task is intentional ownership protection.
- A study day requires at least one completed task. The current streak ends today or yesterday (if today is not complete yet); the longest streak excludes future dates.
- Academic percentages are based on recorded obtained/maximum marks only; no marks-to-grade thresholds are assumed.
- CSV downloads include a UTF-8 BOM, quote and escape CSV fields, and prefix formula-triggering untrusted text with an apostrophe. See the repository README for export endpoint parameters and date-filter semantics.
- Tests and builds run locally; frontend test requests are mocked and do not require a live API.
