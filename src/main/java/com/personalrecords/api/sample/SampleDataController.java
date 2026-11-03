package com.personalrecords.api.sample;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sample-data")
public class SampleDataController {

    private final SampleDataService sampleDataService;

    public SampleDataController(SampleDataService sampleDataService) {
        this.sampleDataService = sampleDataService;
    }

    @PostMapping("/initialize")
    public ResponseEntity<Void> initialize(Authentication authentication) {
        sampleDataService.initializeFor(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
