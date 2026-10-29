package com.personalrecords.api.academic;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/academic-summary", produces = MediaType.APPLICATION_JSON_VALUE)
public class AcademicSummaryController {

    private final AcademicService academicService;

    public AcademicSummaryController(AcademicService academicService) {
        this.academicService = academicService;
    }

    @GetMapping
    public AcademicSummaryResponse summarize(Authentication authentication) {
        return academicService.summarizeAcademicHistory(authentication.getName());
    }
}
