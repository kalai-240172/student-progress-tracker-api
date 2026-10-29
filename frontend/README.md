# Studyflow frontend

Responsive React + TypeScript frontend for the existing Personal Student Record & Progress Tracking API. The frontend consumes the real Spring Boot registration, records, goals, progress-log, task-planner, summary, and CSRF endpoints; it does not provide mock data or an API.

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

Open the local URL printed by Vite (normally `http://localhost:5173`). The Vite development server proxies `/api` requests to `http://localhost:8080` by default, so the browser uses one origin for API calls, Basic credentials, and CSRF cookies.

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

The existing API uses HTTP Basic authentication, not a dedicated login session or token system. The app keeps the Basic authorization value in JavaScript memory only. It never writes raw passwords or credentials to local storage, session storage, cookies, or browser logs. Reloading the page clears the in-memory credentials and signs you out; signing out clears them immediately. This reduces persistence but does not make Basic authentication equivalent to a dedicated session or token flow. Use HTTPS outside local development.

Write requests first fetch `GET /api/csrf`, then send the backend-provided header and token. If a token expires, the client refreshes it and retries a write once. Authentication errors clear the in-memory credentials; `400`, `403`, `404`, `409`, network errors, and validation field errors are displayed without including credentials.

## Features and API routes

- Overview: `GET /api/goals`, `GET /api/goals/{id}/progress`, and `GET /api/goals/{goalId}/logs` power the goal totals, actual status counts, weekly/monthly time, and recent sessions. Statistics are never fabricated.
- Daily tasks: `/tasks` calls `GET /api/tasks`, `GET /api/tasks/today-summary`, and the task create, update, status, actual-duration, and delete endpoints. It supports today, all tasks, and a selected date.
- Calendar: `/calendar` requests the visible month or Monday-to-Sunday week using the inclusive task date-range API and displays per-day task/completion counts.
- Study history: `/history` uses inclusive start/end filters, displays per-day task/time summaries and a planned-versus-recorded-time chart, and fetches owner-scoped overdue tasks and study streaks. Rescheduling updates the existing task rather than creating a replacement.
- Reports: `/reports` reuses the owner-scoped task date-range API for weekly, monthly, and custom reports. Completion rate uses completed tasks divided by all scheduled tasks in-range; actual time includes recorded durations only, and future task dates are excluded.
- Academic progress: `/academic` manages owner-scoped academic years, semesters, and subjects through the typed `/api/academic-years`, `/api/semesters`, and `/api/subjects` APIs. Summaries use the official R-2023 CBCCS grade-point mapping and backend-calculated eligible-course GPA/CGPA; marks do not automatically determine grades.
- CSV exports: `/exports` downloads owner-scoped study history and task data with optional inclusive date ranges, and academic results filtered by academic-year and/or semester IDs. Downloads use authenticated `text/csv` responses as browser files; empty exports contain headers, missing values stay blank, and formula-like text is escaped safely.
- Student records: `GET/POST /api/records`, `GET/PUT/DELETE /api/records/{id}`.
- Goals: `GET/POST /api/goals`, `GET/PUT/DELETE /api/goals/{id}`.
- Goal detail: `GET /api/goals/{goalId}/progress`, `GET/POST /api/goals/{goalId}/logs`, and `GET/PUT/DELETE /api/goals/{goalId}/logs/{logId}`.
- Account access: `POST /api/auth/register`; protected calls use the existing HTTP Basic credentials and CSRF rules.

Dashboard activity uses the backend summary's ISO Monday-to-Monday week and calendar-month boundaries. Total recorded minutes are summed from each goal's saved summary, and recent activity comes from persisted logs. Goal percentage represents time against a duration target, not learning quality.

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
