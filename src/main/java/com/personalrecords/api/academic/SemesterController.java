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
@RequestMapping(path = "/api/semesters", produces = MediaType.APPLICATION_JSON_VALUE)
public class SemesterController {

    private final AcademicService academicService;

    public SemesterController(AcademicService academicService) {
        this.academicService = academicService;
    }

    @GetMapping("/{id}")
    public SemesterResponse get(Authentication authentication, @PathVariable Long id) {
        return academicService.getSemester(authentication.getName(), id);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SemesterResponse update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody SemesterRequest request) {
        return academicService.updateSemester(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        academicService.deleteSemester(authentication.getName(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{id}/summary")
    public SemesterSummaryResponse summarize(Authentication authentication, @PathVariable Long id) {
        return academicService.summarizeSemester(authentication.getName(), id);
    }

    @GetMapping("/{semesterId}/subjects")
    public List<AcademicSubjectResponse> listSubjects(
            Authentication authentication, @PathVariable Long semesterId) {
        return academicService.listSubjects(authentication.getName(), semesterId);
    }

    @PostMapping(path = "/{semesterId}/subjects", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AcademicSubjectResponse> createSubject(
            Authentication authentication,
            @PathVariable Long semesterId,
            @Valid @RequestBody AcademicSubjectRequest request) {
        AcademicSubjectResponse response =
                academicService.createSubject(authentication.getName(), semesterId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/subjects/{id}")
                .replaceQuery(null)
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }
}
