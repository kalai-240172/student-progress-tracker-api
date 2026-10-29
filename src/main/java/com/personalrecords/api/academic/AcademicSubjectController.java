package com.personalrecords.api.academic;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/api/subjects", produces = MediaType.APPLICATION_JSON_VALUE)
public class AcademicSubjectController {

    private final AcademicService academicService;

    public AcademicSubjectController(AcademicService academicService) {
        this.academicService = academicService;
    }

    @GetMapping("/{id}")
    public AcademicSubjectResponse get(Authentication authentication, @PathVariable Long id) {
        return academicService.getSubject(authentication.getName(), id);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AcademicSubjectResponse update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AcademicSubjectRequest request) {
        return academicService.updateSubject(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        academicService.deleteSubject(authentication.getName(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
