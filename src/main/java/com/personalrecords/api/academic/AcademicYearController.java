package com.personalrecords.api.academic;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(path = "/api/academic-years", produces = MediaType.APPLICATION_JSON_VALUE)
public class AcademicYearController {

    private final AcademicService academicService;

    public AcademicYearController(AcademicService academicService) {
        this.academicService = academicService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AcademicYearResponse> create(
            Authentication authentication, @Valid @RequestBody AcademicYearRequest request) {
        AcademicYearResponse response = academicService.createYear(authentication.getName(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<AcademicYearResponse> list(Authentication authentication) {
        return academicService.listYears(authentication.getName());
    }

    @GetMapping("/{id}")
    public AcademicYearResponse get(Authentication authentication, @PathVariable Long id) {
        return academicService.getYear(authentication.getName(), id);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AcademicYearResponse update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AcademicYearRequest request) {
        return academicService.updateYear(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        academicService.deleteYear(authentication.getName(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{yearId}/semesters")
    public List<SemesterResponse> listSemesters(
            Authentication authentication, @PathVariable Long yearId) {
        return academicService.listSemesters(authentication.getName(), yearId);
    }

    @PostMapping(path = "/{yearId}/semesters", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SemesterResponse> createSemester(
            Authentication authentication,
            @PathVariable Long yearId,
            @Valid @RequestBody SemesterRequest request) {
        SemesterResponse response = academicService.createSemester(authentication.getName(), yearId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/semesters/{id}")
                .replaceQuery(null)
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}/summary")
    public AcademicYearSummaryResponse summarize(
            Authentication authentication, @PathVariable Long id) {
        return academicService.summarizeYear(authentication.getName(), id);
    }
}
