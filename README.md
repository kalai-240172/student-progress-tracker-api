# Personal Student Record & Progress Tracking API

A Spring Boot API for account registration, secure personal student records, learning goals, and daily progress logs. Users can define goals and track study time toward them without exposing data to other accounts.

## Prerequisites

- Windows 11 and PowerShell (commands below use PowerShell)
- Java 17
- The included Maven Wrapper (`mvnw.cmd`); a separate Maven installation is unnecessary

The project uses Spring Boot 4.1.2, Spring WebMVC, Spring Security, Spring Data JPA, Jakarta Validation, and H2.

## Run and test

From the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

The API starts at `http://localhost:8080`. Run the automated tests with:

```powershell
.\mvnw.cmd clean test
```

Tests use an isolated in-memory H2 database and do not access the local persistent database.

The public health check is `GET /api/health` and returns HTTP 200:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

## Registration and authentication

`POST /api/auth/register` is public and accepts:

```json
{
  "name": "Arun Kumar",
  "email": "arun@example.com",
  "password": "ReplaceWithYourOwnStrongPassword!"
}
```

Names must be nonblank and at most 100 characters. Emails must be valid and at most 254 characters; they are trimmed and lowercased before storage and lookup. Passwords must be between 12 and 72 characters and no more than 72 UTF-8 bytes. Passwords are passed unchanged to BCrypt; registration never trims or returns the password. Successful registration returns HTTP 201 and a safe response containing the account ID, name, normalized email, and creation timestamp. Invalid input returns HTTP 400; an existing normalized email returns HTTP 409.

All endpoints other than `GET /api/health` and `POST /api/auth/register` require HTTP Basic authentication using the registered email and password. Spring Security verifies passwords against BCrypt hashes; plain-text passwords are not stored. Invalid or missing credentials return HTTP 401.

## Student records

Authenticated users can create and manage only their own records. A record has a required title (up to 150 characters), category, and status; description (up to 2000 characters), institution (up to 200 characters), and start/end dates are optional. If both dates are supplied, the end date cannot be earlier than the start date.

Categories: `ACADEMIC`, `CERTIFICATE`, `PROJECT`, `ACHIEVEMENT`.

Statuses: `IN_PROGRESS`, `COMPLETED`, `PLANNED`.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/records` | Create a record; returns HTTP 201 and a `Location` header |
| `GET` | `/api/records` | List the authenticated user's records |
| `GET` | `/api/records/{id}` | Get one of the authenticated user's records |
| `PUT` | `/api/records/{id}` | Replace an owned record's editable fields |
| `DELETE` | `/api/records/{id}` | Delete an owned record; returns HTTP 204 |

Create and update requests use this JSON shape:

```json
{
  "title": "Database Systems",
  "description": "Completed the relational database course",
  "category": "ACADEMIC",
  "institution": "Example University",
  "startDate": "2026-01-15",
  "endDate": "2026-05-30",
  "status": "COMPLETED"
}
```

Requests to access another user's record return HTTP 404. Responses include record fields and timestamps, but never the associated user or account information. Invalid input returns HTTP 400 with the existing JSON error format.

## Goals and daily progress

Goals are private to the authenticated user. Each goal has a required title, category, positive target duration in minutes, and status; description and target date are optional. Categories are `STUDY`, `PROJECT`, `CAREER`, and `PERSONAL`. Statuses are `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, and `CANCELLED`. Preferred weekdays are optional ISO weekday names (`MONDAY` through `SUNDAY`). They are persisted as enum strings in a separate `goal_preferred_weekdays` collection table, with one row per selected day.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/goals` | Create a goal; returns HTTP 201 and a `Location` header |
| `GET` | `/api/goals` | List the authenticated user's goals |
| `GET` | `/api/goals/{id}` | Get one of the authenticated user's goals |
| `PUT` | `/api/goals/{id}` | Replace an owned goal's editable fields |
| `DELETE` | `/api/goals/{id}` | Delete an owned goal and its progress logs; returns HTTP 204 |
| `POST` | `/api/goals/{goalId}/logs` | Add a progress log to an owned goal |
| `GET` | `/api/goals/{goalId}/logs` | List logs for an owned goal |
| `GET` | `/api/goals/{goalId}/logs/{logId}` | Get a log for an owned goal |
| `PUT` | `/api/goals/{goalId}/logs/{logId}` | Update a log for an owned goal |
| `DELETE` | `/api/goals/{goalId}/logs/{logId}` | Delete a log for an owned goal |
| `GET` | `/api/goals/{goalId}/progress` | Get a goal's calculated progress summary |

Create or update a goal with:

```json
{
  "title": "Learn database systems",
  "description": "Study relational databases and SQL",
  "category": "STUDY",
  "targetDate": "2026-12-31",
  "targetDurationMinutes": 1200,
  "preferredWeekdays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "status": "IN_PROGRESS"
}
```

Add a daily progress log with:

```json
{
  "activityDate": "2026-10-29",
  "timeSpentMinutes": 45,
  "summary": "Practiced joins and aggregate queries",
  "notes": "Reviewed the course exercises"
}
```

Activity dates may be in the past; the API does not impose an arbitrary historical cutoff. Goal, log, and progress-summary requests are scoped to the authenticated owner. Requests for inaccessible goals or logs return HTTP 404. Goal deletion also deletes its logs.

The progress summary includes the target duration, total and remaining minutes, percentage, log count, current-week and current-month minutes, and most recent activity date. Total minutes are summed from saved logs. Remaining minutes are `max(target - total, 0)`. Percentage is `min(total / target * 100, 100)`, rounded to two decimal places using half-up rounding; it measures time recorded against the duration target, not learning quality. The current week uses the ISO Monday-to-Monday interval (Monday included, next Monday excluded); the current month uses the first day of this month through the first day of next month (start included, end excluded). Both boundaries are calculated from the server's local date.

## CSRF for API clients

CSRF protection remains enabled for state-changing requests; only public account registration is excluded. After authenticating with HTTP Basic, fetch a token from `GET /api/csrf`, retain the session cookie, and send the returned token using the returned header name (normally `X-CSRF-TOKEN`) on `POST`, `PUT`, and `DELETE` requests for records, goals, and logs. Do not disable CSRF to use the APIs.

For example, with PowerShell, keep a web session and supply the token header:

```powershell
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$credentials = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("arun@example.com:ReplaceWithYourOwnStrongPassword!"))
$authHeaders = @{ Authorization = "Basic $credentials" }
$csrf = Invoke-RestMethod http://localhost:8080/api/csrf -WebSession $session -Headers $authHeaders
$headers = @{ Authorization = "Basic $credentials" }
$headers[$csrf.headerName] = $csrf.token
$body = @{
    title = "Database Systems"
    category = "ACADEMIC"
    status = "COMPLETED"
    startDate = "2026-01-15"
    endDate = "2026-05-30"
} | ConvertTo-Json
Invoke-RestMethod http://localhost:8080/api/records -Method Post -WebSession $session -Headers $headers -ContentType "application/json" -Body $body
```

Use the same session and CSRF header for subsequent record, goal, and log changes. The example uses a placeholder password; register an account and substitute your own credentials.

## Local database

The default H2 database is file-based and stored outside the repository at:

```text
%USERPROFILE%\.personal-records\personalrecords.mv.db
```

The application creates the `.personal-records` directory as needed. H2's web console is disabled. Override settings with `PERSONAL_RECORDS_DB_URL`, `PERSONAL_RECORDS_DB_USERNAME`, and `PERSONAL_RECORDS_DB_PASSWORD`; do not commit local credentials or database files. Tests use a separate in-memory database.

Stop the application before making a backup. Copy `personalrecords.mv.db` to a secure backup location; restore by stopping the application and copying the backup back to the same database location. Keep backups private and periodically verify that they can be restored. File-based H2 is for local development only and is not suitable for public production deployment.

The application uses JPA to keep a future PostgreSQL migration practical. Production use will require a managed database, appropriate schema migrations, secret management, transport security, and a production-ready authentication design.

## Scope and limitations

This checkpoint implements accounts, personal student records, goals, and daily progress logs. It does not implement a frontend, schedules, or other progress-tracking workflows.
