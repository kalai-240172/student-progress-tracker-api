package com.personalrecords.api.exports;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping(path = "/api/exports")
public class CsvExportController {

    private static final MediaType CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private final CsvExportService exportService;

    public CsvExportController(CsvExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping(path = "/study-history", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> studyHistory(
            Authentication authentication,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return download("study-history.csv",
                exportService.exportStudyHistory(authentication.getName(), startDate, endDate));
    }

    @GetMapping(path = "/tasks", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> tasks(
            Authentication authentication,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return download("tasks.csv",
                exportService.exportTasks(authentication.getName(), startDate, endDate));
    }

    @GetMapping(path = "/academic", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> academic(
            Authentication authentication,
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(required = false) Long semesterId) {
        return download("academic-results.csv",
                exportService.exportAcademic(authentication.getName(), academicYearId, semesterId));
    }

    private ResponseEntity<StreamingResponseBody> download(String filename, StreamingResponseBody body) {
        return ResponseEntity.ok()
                .contentType(CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
