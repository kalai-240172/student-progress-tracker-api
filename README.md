# Personal Student Record & Progress Tracking API

A Spring Boot API foundation for secure account registration and future student progress tracking. This checkpoint implements registration, database-backed HTTP Basic authentication, and persistent local development storage. Academic records and progress features are not included yet.

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

The API starts at `http://localhost:8080`. Automated tests run without a separate application server:

```powershell
.\mvnw.cmd clean test
```

The public health check is `GET /api/health` and returns HTTP 200:

```json
{"status":"healthy"}
```

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

## Registration

`POST /api/auth/register` accepts JSON without prior authentication:

```json
{
  "name": "Arun Kumar",
  "email": "arun@example.com",
  "password": "ReplaceWithYourOwnStrongPassword!"
}
```

Names must be nonblank and at most 100 characters. Emails must be valid and at most 254 characters; they are trimmed and lowercased before storage and lookup. Passwords must be between 12 and 72 characters and no more than 72 UTF-8 bytes. Passwords are passed unchanged to BCrypt; registration never trims or returns the password. Successful registration returns HTTP 201 and a safe response:

```json
{
  "id": 1,
  "name": "Arun Kumar",
  "email": "arun@example.com",
  "createdAt": "2026-10-29T12:00:00"
}
```

Invalid input returns HTTP 400, an existing normalized email returns HTTP 409, and unexpected errors return a generic HTTP 500 response. Duplicate accounts are also protected by a database unique constraint to cover concurrent registrations.

## Authentication and security

All application endpoints other than `GET /api/health` and `POST /api/auth/register` require HTTP Basic authentication using the registered email and password. Spring Security verifies passwords against BCrypt hashes; plain-text passwords are not stored. Invalid or missing credentials return HTTP 401. There are no default accounts or hard-coded credentials.

CSRF protection remains enabled. Only the unauthenticated registration POST is excluded because it creates an account and does not act on an existing authenticated user's state. Future authenticated browser-originating state changes must retain CSRF protection and provide a CSRF token. HTTP Basic is intended for initial local API testing, not as a production browser authentication design.

## Local database

The default H2 database is file-based and stored outside the repository at:

```text
%USERPROFILE%\.personal-records\personalrecords.mv.db
```

The application creates the `.personal-records` directory as needed. H2's web console is disabled. Override settings with `PERSONAL_RECORDS_DB_URL`, `PERSONAL_RECORDS_DB_USERNAME`, and `PERSONAL_RECORDS_DB_PASSWORD`; do not commit local credentials or database files. Tests use a separate in-memory database and never connect to the persistent development database.

Stop the application before making a backup. Copy `personalrecords.mv.db` to a secure backup location; restore by stopping the application and copying the backup back to the same database location. Keep backups private and periodically verify that they can be restored. File-based H2 is for local development only and is not suitable for public production deployment.

The repository and entity model use JPA to keep a future PostgreSQL migration practical. Production use will require a managed database, appropriate schema migrations, secret management, transport security, and a production-ready authentication design.

## Scope and limitations

This checkpoint does not implement a frontend, goals, academic records, schedules, daily logs, certificates, achievements, or progress tracking features. Those are future work.
