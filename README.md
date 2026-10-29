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

## Daily task planner

Daily tasks let each authenticated user plan study work by title, subject, date, and estimated minutes, then update task status and record actual time independently. New tasks start as `PENDING`; completing a task does not fill in actual duration. Editing a task's planned date reschedules the same task without replacing its ID. Past-dated tasks remain in their selected status until explicitly changed.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/tasks` | Create a task; returns HTTP 201 |
| `GET` | `/api/tasks` | List tasks; optionally filter with `date=YYYY-MM-DD` or both `startDate` and `endDate` |
| `GET` | `/api/tasks/{id}` | Get an owned task |
| `PUT` | `/api/tasks/{id}` | Update task details and planned date |
| `PATCH` | `/api/tasks/{id}/status` | Change status using `{"status":"IN_PROGRESS"}` |
| `PATCH` | `/api/tasks/{id}/actual-duration` | Record non-negative minutes using `{"actualDurationMinutes":45}` |
| `DELETE` | `/api/tasks/{id}` | Delete an owned task; returns HTTP 204 |
| `GET` | `/api/tasks/today-summary` | Get today's task counts and planned/recorded minutes |

Date range endpoints are inclusive and require both dates, with `startDate` on or before `endDate`. The `date` filter cannot be combined with a range. Today is based on the server's local date. Summary actual minutes sum only values recorded for today's tasks; missing values are not inferred. All task operations use the authenticated user and inaccessible task IDs return HTTP 404. The authenticated frontend includes the Daily tasks page at `/tasks`.

## Calendar and study history

The authenticated frontend provides a responsive month/week calendar at `/calendar` and study history at `/history`. The calendar uses Monday-first weeks, includes adjacent-month dates in the month grid, shows per-date task counts/completion, and loads the displayed period using inclusive `startDate`/`endDate` task filters. Selecting a date shows its tasks.

History applies an inclusive start/end date range. It groups tasks by planned date and reports planned minutes, explicitly recorded actual minutes (missing values count as zero in the aggregate), completed-task count, and completion rate (`completed / task count`, zero when there are no tasks). Its chart compares daily planned and actual minutes. For a selected range, all fetched tasks belong to the authenticated user and fall within those inclusive dates.

Study day and streak definitions:

- A study day is a calendar date with at least one task whose status is `COMPLETED`.
- The current streak counts consecutive study days ending today, or yesterday when today has no completed task yet. Otherwise it is zero.
- The longest streak is the largest consecutive run of completed-task dates up through today.
- Multiple completed tasks on one date count as one study day. Future-dated completions do not count.

`GET /api/tasks/study-streaks` returns current and longest streak lengths. It queries distinct completed dates for the authenticated user rather than loading task entities. `GET /api/tasks/overdue` returns that user's tasks planned before the server's local today whose status is not `COMPLETED`. The history page can reschedule one by updating its planned date; this preserves its task ID and status. Tasks are never automatically completed or removed as a consequence of being overdue.

## Reports dashboard

The authenticated Reports page is available at `/reports`. It reuses `GET /api/tasks?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`; no separate reporting endpoint or reporting data model is required. Weekly and monthly periods use inclusive start/end dates and stop at today. Custom ranges also require both dates, include both boundaries, and cannot end in the future. Only persisted task rows returned for the authenticated user are included, and task IDs are deduplicated before calculations.

- Completed task count is the number of tasks with status `COMPLETED`.
- Completion rate is completed tasks divided by **all tasks planned in the selected period**, including pending and in-progress tasks; an empty period reports zero rather than dividing by zero.
- Planned minutes sum estimated durations for all tasks in the period.
- Actual minutes sum only explicitly recorded actual durations. Missing actual durations contribute zero and are not inferred from estimates. The UI labels actual time as recorded.
- Daily charts show planned versus recorded actual minutes and completed versus not-completed task counts. Dates without tasks are omitted rather than filled with fabricated activity.
- Tasks planned for a future date are excluded from reports.

The report query uses the existing authenticated-user-scoped task list; no client-supplied user identifier is accepted. Authentication and CSRF behavior are unchanged.

## Academic progress

The authenticated Academic Progress page is available at `/academic`. Each academic year belongs to the authenticated user; semesters are accessed through their year, and subjects through their semester. Academic data responses use DTOs and never include user or account details. No sample subjects or marks are created.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET`, `POST` | `/api/academic-years` | List or create academic years |
| `GET`, `PUT`, `DELETE` | `/api/academic-years/{id}` | Retrieve, edit, or delete an owned academic year |
| `GET`, `POST` | `/api/academic-years/{yearId}/semesters` | List or create semesters within an owned year |
| `GET`, `PUT`, `DELETE` | `/api/semesters/{id}` | Retrieve, edit, or delete an owned semester |
| `GET`, `POST` | `/api/semesters/{semesterId}/subjects` | List or create subjects within an owned semester |
| `GET`, `PUT`, `DELETE` | `/api/subjects/{id}` | Retrieve, edit, or delete an owned subject |
| `GET` | `/api/semesters/{id}/summary` | Return subject marks and semester GPA |
| `GET` | `/api/academic-years/{id}/summary` | Return semester summaries and year CGPA |
| `GET` | `/api/academic-summary` | Return CGPA and summaries across the authenticated user's academic history |

Academic-year labels and semester labels are required; dates are optional, but when both dates are supplied the end date cannot precede the start date. Subject names are required, credits and maximum marks must be positive, and obtained marks (when recorded) must be between zero and the maximum. Marks support up to two decimal places. Subject codes are optional and unique within a semester without regard to case. Deleting a semester with subjects or an academic year with semesters returns HTTP 409; delete its children first. No operation cascades across academic records.

The default official R-2023 CBCCS mapping is `O = 10`, `A+ = 9`, `A = 8`, `B+ = 7`, `B = 6`, `C = 5`, and `U = 0`. The letter grade is recorded directly, separately from its derived grade point. The supplied college document does not specify marks-to-grade boundaries, so percentage never assigns or predicts a grade.

Percentage is `(obtained marks / maximum marks) × 100`, rounded to two decimal places using half-up rounding; it is unavailable when marks are missing. Semester GPA and cumulative CGPA use `Σ(credits × grade points) / Σ(eligible credits)`, rounded to two decimal places using half-up rounding. Only subjects with an official grade other than `U` are successfully cleared and eligible. `U` and missing-grade subjects are excluded from both weighted points and the eligible-credit denominator; missing marks do not imply a grade. When no eligible credits exist, GPA/CGPA is unavailable (`null`). The backend is authoritative for all academic calculations.

Academic create/update/delete requests use the same authenticated HTTP Basic and CSRF conventions as other protected writes. To run backend tests, use `.\mvnw.cmd clean test` from the repository root. To run frontend tests and build, use `npm test -- --maxWorkers=1` and `npm run build` from `frontend`.

## CSV data exports

The authenticated frontend provides an **Export data** page at `/exports`. It downloads the current user's study history, daily tasks, or academic results as UTF-8 CSV files. The endpoints require HTTP Basic authentication and use the authenticated principal for ownership; client-supplied user IDs are not accepted. The endpoints are read-only `GET` requests and follow the existing CSRF policy.

| Method | Endpoint | Query parameters | Export |
| --- | --- | --- | --- |
| `GET` | `/api/exports/study-history` | Optional `startDate=YYYY-MM-DD` and `endDate=YYYY-MM-DD` | Saved progress logs with goal, activity date, time spent, summary, notes, and timestamps |
| `GET` | `/api/exports/tasks` | Optional `startDate=YYYY-MM-DD` and `endDate=YYYY-MM-DD` | Task title, description, subject, planned date, status, estimated and recorded actual minutes, and timestamps |
| `GET` | `/api/exports/academic` | Optional `academicYearId` and/or `semesterId` | Existing academic year, semester, subject, marks, percentage, official grade, grade point, and available GPA/CGPA summaries |

Study-history dates filter the log activity date; task dates filter the planned date. Supply both dates or neither; both endpoints include the start and end dates using the server's `LocalDate` calendar. Academic identifiers are checked against the authenticated user's ownership, and a semester must belong to a supplied academic year. Invalid ranges return HTTP 400; inaccessible academic identifiers return HTTP 404. Empty results still return a valid CSV with its header row. Missing values are emitted as empty fields rather than zero or inferred data. Academic percentages and summaries reuse the existing authoritative academic calculations.

Files include a UTF-8 byte-order mark for spreadsheet compatibility. Every field is CSV-quoted, embedded quotes are doubled, and commas and line breaks remain within their quoted field. Untrusted text beginning with `=`, `+`, `-`, or `@` (including after leading whitespace/control characters) is prefixed with an apostrophe to reduce spreadsheet formula execution risk. Numeric values are exported as numeric text and are not rewritten; this preserves legitimate negative numbers. The export page explains the encoding and safety behavior.

## CSRF for API clients

CSRF protection remains enabled for state-changing requests; only public account registration is excluded. After authenticating with HTTP Basic, fetch a token from `GET /api/csrf`, retain the session cookie, and send the returned token using the returned header name (normally `X-CSRF-TOKEN`) on `POST`, `PUT`, `PATCH`, and `DELETE` requests for records, goals, logs, tasks, and academic data. Do not disable CSRF to use the APIs.

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

Use the same session and CSRF header for subsequent record, goal, log, and task changes. The example uses a placeholder password; register an account and substitute your own credentials.

## Local database

The default H2 database is file-based and stored outside the repository at:

```text
%USERPROFILE%\.personal-records\personalrecords.mv.db
```

The application creates the `.personal-records` directory as needed. H2's web console is disabled. Override settings with `PERSONAL_RECORDS_DB_URL`, `PERSONAL_RECORDS_DB_USERNAME`, and `PERSONAL_RECORDS_DB_PASSWORD`; do not commit local credentials or database files. Tests use a separate in-memory database. Do not copy the live H2 database file as a backup; use the supported offline SQL backup and guarded recovery procedure below.

The application uses JPA to keep a future PostgreSQL migration practical. Production use will require a managed database, appropriate schema migrations, secret management, transport security, and a production-ready authentication design.

## Safe database backup and recovery

The application uses embedded H2 `2.4.240` in file mode by default: `%USERPROFILE%\.personal-records\personalrecords.mv.db`. Tests use `jdbc:h2:mem:personalrecords-test` and are never a backup source. No backup endpoint is exposed. The operator helper uses the project's resolved H2 runtime and H2's supported `SCRIPT`/`RunScript` tools to create and recover a UTF-8 SQL script, not a byte-for-byte copy of a live database. It only accepts `jdbc:h2:file:` URLs and refuses memory/server URLs or a missing source database file. Stop the application before creating a backup; the script deliberately opens the embedded database offline and does not copy active database files.

Backups are stored by default in `%USERPROFILE%\.personal-records-backups`, separate from the active database directory and outside the repository. Set `PERSONAL_RECORDS_BACKUP_DIR` or pass `-BackupDirectory` to choose another location; the helper refuses a destination inside this Git repository or active database directory. It estimates required free space, writes to a unique temporary file, signs its exact contents with HMAC-SHA-256, then publishes a timestamped UTC `.sql` file and matching `.sql.hmac` signature without replacing existing files. Validation requires the H2 script header and a matching signature. The HMAC key is a Base64-encoded secret of at least 32 bytes; generate it once from a cryptographically secure source and keep it in a trusted password/secret manager separate from the backups. Do not lose or rotate the key without retaining the prior key for backups signed with it. The helper never prints the key.

Before each backup, validation, or recovery session, load the same key from your trusted secret store into the current PowerShell process. This prompt does not echo the key:

```powershell
$secureKey = Read-Host "Base64 backup key from your secret manager" -AsSecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $env:PERSONAL_RECORDS_BACKUP_HMAC_KEY =
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    $secureKey.Dispose()
}
```

The value is present in the current PowerShell process environment so the helper can pass it to its child process; clear it with `Remove-Item Env:\PERSONAL_RECORDS_BACKUP_HMAC_KEY` when finished. Do not put the key literal in scripts, command history, the database URL, or source control. Keep the key separately backed up; losing it makes helper validation/recovery of its signed backups impossible.

From the repository root, stop the running application first (for `spring-boot:run`, press Ctrl+C in its console), then run:

```powershell
.\scripts\database-backup.ps1 -Action Backup

$backup = Get-ChildItem "$HOME\.personal-records-backups\personalrecords-backup-*.sql" |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
if ($null -eq $backup) { throw "No backup file was created." }
.\scripts\database-backup.ps1 -Action Validate -BackupPath $backup.FullName
```

For a custom persistent database, set `PERSONAL_RECORDS_DB_URL` and, when needed, `PERSONAL_RECORDS_DB_USERNAME` / `PERSONAL_RECORDS_DB_PASSWORD` in the same PowerShell environment before running the helper. The default credentials are `sa` with an empty password, matching the default development configuration. Do not put credentials in command-line arguments, URLs, scripts, or source control. The helper does not print passwords or the HMAC key. Keep the SQL script and its signature private: the script contains the application's stored user data (including password hashes). Store them in a user-only, access-controlled directory and encrypted storage; define and regularly review a retention policy. The default directory is outside Git, and narrow ignore rules cover conventional repository-local backup/restore artifact names.

### Recovery without overwriting the current database

Recovery is deliberately split into validation and a later operator-controlled switch. It never writes to the configured source database, never restores at startup, and refuses an existing temporary database. **Treat `.sql` backups as executable input.** Restore only a file whose `.sql.hmac` signature validates with the separately protected key. This detects file replacement or modification when the key remains secret; it does not make an already-compromised source database trustworthy. Never restore an untrusted or unsigned SQL file. Keep the application stopped while running the restore helper. First select and validate the intended backup:

```powershell
.\scripts\database-backup.ps1 -Action Backup

$safetyBackup = Get-ChildItem "$HOME\.personal-records-backups\personalrecords-backup-*.sql" |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
if ($null -eq $safetyBackup) { throw "The pre-recovery safety backup was not created." }
.\scripts\database-backup.ps1 -Action Validate -BackupPath $safetyBackup.FullName

$backup = Get-Item "C:\path\to\personalrecords-backup-YYYYMMDDTHHMMSS.mmmZ-random.sql"
.\scripts\database-backup.ps1 -Action Validate -BackupPath $backup.FullName
```

Restore into a new, empty file database outside the active database directory and repository. The database basename must include `restore` or `recovery`; do not point it at `personalrecords-test` or any existing database:

```powershell
$restoreBase = Join-Path $HOME ".personal-records-recovery\personalrecords-restore-20261030"
$restoreUrl = "jdbc:h2:file:$restoreBase;DB_CLOSE_ON_EXIT=FALSE"
.\scripts\database-backup.ps1 -Action RestoreToTemporary `
    -BackupPath $backup.FullName `
    -TemporaryDatabaseUrl $restoreUrl
```

The helper verifies the HMAC before executing the H2 SQL script with the project's H2 version and confirms a non-empty `.mv.db` file was produced. To verify actual application startup/data, start a separate local instance pointed only at the temporary URL, using the same configured database username/password as the source. It creates a new database with those credentials; they are read from the existing environment, not supplied on the command line:

```powershell
$env:PERSONAL_RECORDS_DB_URL = $restoreUrl
.\mvnw.cmd spring-boot:run
```

Inspect the recovered data through the application, then stop it with Ctrl+C. Do not configure this temporary recovery URL for a test run; automated tests are isolated by `src/test/resources/application.properties`.

To switch to the verified recovered database, deliberately change the application's `PERSONAL_RECORDS_DB_URL` in the environment or deployment configuration to `$restoreUrl`, then start the application. This switches to a separate database without replacing the original files. Preserve the old URL and backup. If recovery validation or startup fails, stop the process and restore the previous `PERSONAL_RECORDS_DB_URL` value (or unset the override to return to the default `%USERPROFILE%\.personal-records\personalrecords` database), then restart. The original database remains untouched and is the rollback path. Do not copy recovered files over the original database. Retain the recovery database until the new instance is confirmed and the rollback period has passed.

Backups created before HMAC signing was added are unsigned and are rejected by helper validation/restore. Create a new signed backup from a trusted source database; do not generate a signature for an old file unless its provenance has been independently established. The helper only supports embedded file-based H2 and SQL scripts compatible with the project's H2 version. Custom server-mode, in-memory, or differently encrypted H2 URLs require an operator-specific procedure and are refused rather than guessed. The helper does not automatically remove old backups, failed temporary restores, or databases; inspect any exact path before manually cleaning it up.

## Scope and limitations

This project implements accounts, personal student records, goals, daily progress logs, a React frontend, a daily task planner, a calendar, study history, a task reports dashboard, and owner-scoped academic years, semesters, subjects, marks, grades, GPA, and CGPA. It does not automatically schedule recurring tasks, mark overdue tasks as completed, or infer grades from marks.
