import { afterEach, describe, expect, it, vi } from "vitest";
import { api, clearCredentials, setCredentials } from "./client";

afterEach(() => {
  clearCredentials();
  vi.restoreAllMocks();
});

describe("API client", () => {
  it("uses backend field errors for validation responses", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            status: 400,
            message: "Validation failed",
            fieldErrors: { title: "Title is required" },
          }),
          { status: 400, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    await expect(api.listGoals()).rejects.toMatchObject({
      status: 400,
      message: "Validation failed",
      fieldErrors: { title: "Title is required" },
    });
  });

  it("gets CSRF before writes and handles 204 without parsing JSON", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "csrf-value" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    setCredentials("student@example.com", "StrongPassword123!");

    await expect(api.deleteRecord(42)).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBeInstanceOf(URL);
    const requestHeaders = new Headers((fetchMock.mock.calls[1][1] as RequestInit).headers);
    expect(requestHeaders.get("X-CSRF-TOKEN")).toBe("csrf-value");
  });

  it("refreshes an expired CSRF token and retries a protected write once", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "expired" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(new Response("Forbidden", { status: 403 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "fresh" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify([]), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        }),
      );
    vi.stubGlobal("fetch", fetchMock);
    setCredentials("student@example.com", "StrongPassword123!");

    await api.createRecord({
      title: "Project",
      description: null,
      category: "PROJECT",
      institution: null,
      startDate: null,
      endDate: null,
      status: "IN_PROGRESS",
    });

    expect(fetchMock).toHaveBeenCalledTimes(4);
    const requestHeaders = new Headers((fetchMock.mock.calls[3][1] as RequestInit).headers);
    expect(requestHeaders.get("X-CSRF-TOKEN")).toBe("fresh");
  });

  it("does not request a CSRF token to register a public account", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: 1, name: "Student", email: "student@example.com" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await api.register({
      name: "Student",
      email: "student@example.com",
      password: "StrongPassword123!",
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBeInstanceOf(URL);
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe("POST");
  });

  it("uses date filters for tasks and sends CSRF-protected task updates", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify([]), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "task-csrf" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 8, status: "COMPLETED" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    vi.stubGlobal("fetch", fetchMock);
    setCredentials("student@example.com", "StrongPassword123!");

    await api.listTasks({ startDate: "2026-10-28", endDate: "2026-10-29" });
    await api.updateTaskStatus(8, "COMPLETED");

    expect(String(fetchMock.mock.calls[0][0])).toContain(
      "/api/tasks?startDate=2026-10-28&endDate=2026-10-29",
    );
    const requestHeaders = new Headers((fetchMock.mock.calls[2][1] as RequestInit).headers);
    expect(requestHeaders.get("X-CSRF-TOKEN")).toBe("task-csrf");
    expect(JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string)).toEqual({
      status: "COMPLETED",
    });
  });

  it("uses the existing authenticated API configuration for academic reads and writes", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 }))
      .mockResolvedValueOnce(new Response(
        JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "academic-csrf" }),
        { status: 200 },
      ))
      .mockResolvedValueOnce(new Response(
        JSON.stringify({ id: 4, label: "2026-2027" }),
        { status: 201 },
      ));
    vi.stubGlobal("fetch", fetchMock);
    setCredentials("student@example.com", "StrongPassword123!");

    await api.listAcademicYears();
    await api.createAcademicYear({
      label: "2026-2027",
      startDate: "2026-08-01",
      endDate: "2027-05-31",
    });

    expect(String(fetchMock.mock.calls[0][0])).toContain("/api/academic-years");
    const write = fetchMock.mock.calls[2][1] as RequestInit;
    expect(new Headers(write.headers).get("X-CSRF-TOKEN")).toBe("academic-csrf");
    expect(JSON.parse(write.body as string)).toEqual({
      label: "2026-2027",
      startDate: "2026-08-01",
      endDate: "2027-05-31",
    });
  });

  it("downloads CSV responses with authenticated inclusive date parameters and server filenames", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("\uFEFF\"taskId\",\"title\"\r\n", {
        status: 200,
        headers: {
          "Content-Type": "text/csv;charset=UTF-8",
          "Content-Disposition": 'attachment; filename="tasks.csv"',
        },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    setCredentials("student@example.com", "StrongPassword123!");

    const result = await api.exportTasks({ startDate: "2026-04-10", endDate: "2026-04-12" });

    expect(String(fetchMock.mock.calls[0][0])).toContain(
      "/api/exports/tasks?startDate=2026-04-10&endDate=2026-04-12",
    );
    expect(new Headers((fetchMock.mock.calls[0][1] as RequestInit).headers).get("Authorization"))
      .toMatch(/^Basic /);
    expect(result.filename).toBe("tasks.csv");
    expect(result.blob).toBeInstanceOf(Blob);
    expect(result.blob.size).toBeGreaterThan(0);
  });

  it("encodes academic export identifiers as query parameters", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("headers", {
        status: 200,
        headers: { "Content-Disposition": "attachment; filename=academic-results.csv" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await api.exportAcademic({ academicYearId: 7, semesterId: 11 });

    expect(String(fetchMock.mock.calls[0][0])).toContain(
      "/api/exports/academic?academicYearId=7&semesterId=11",
    );
  });
});
