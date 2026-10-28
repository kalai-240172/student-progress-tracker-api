# Personal Student Record & Progress Tracking API

Initial Spring Boot setup for the Personal Student Record & Progress Tracking System.

## Prerequisites

- Windows 11 (commands below use PowerShell)
- Java 17
- Maven Wrapper included in this repository; a separate Maven installation is not required

## Technology

- Spring Boot 4.1.2 with Maven-managed dependency versions
- Spring WebMVC for HTTP endpoints
- Spring Data JPA for persistence
- Spring Security for endpoint protection
- H2 for the temporary development database
- Jakarta Validation for request validation
- Spring Boot test starters, JUnit 5, and MockMvc for automated tests

## Run

From the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

The API starts at `http://localhost:8080`. The unauthenticated health check is:

```text
GET http://localhost:8080/api/health
```

Expected response (HTTP 200):

```json
{"status":"healthy"}
```

PowerShell check from another terminal:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

## Test

Run the integration tests without starting a separate application server:

```powershell
.\mvnw.cmd clean test
```

The tests use MockMvc and verify the public health response and rejection of an unauthenticated request to a protected endpoint. Maven prints the test results and exits with a success or failure code in the same terminal.

## Development Limitations

- The database is in-memory H2 at `jdbc:h2:mem:personalrecords`. All data is lost when the application stops. It is not a persistent or production database.
- The H2 web console is disabled. No database is exposed through a browser endpoint.
- `GET /api/health` is the only public application route. All other routes require authentication; form login is disabled and HTTP Basic returns an unauthorized response when credentials are missing. No user accounts or production authentication mechanism are configured yet, so protected routes are not usable until authentication is implemented.
- No production credentials are configured. This setup is for local development only and is not production-ready.
