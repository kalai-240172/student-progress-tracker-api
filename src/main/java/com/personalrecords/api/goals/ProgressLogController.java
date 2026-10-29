package com.personalrecords.api.goals;

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
@RequestMapping(path = "/api/goals/{goalId}/logs", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProgressLogController {

    private final ProgressLogService logService;

    public ProgressLogController(ProgressLogService logService) {
        this.logService = logService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProgressLogResponse> create(
            Authentication authentication,
            @PathVariable Long goalId,
            @Valid @RequestBody ProgressLogRequest request) {
        ProgressLogResponse response = logService.create(authentication.getName(), goalId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{logId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<ProgressLogResponse> findAll(Authentication authentication, @PathVariable Long goalId) {
        return logService.findAll(authentication.getName(), goalId);
    }

    @GetMapping("/{logId}")
    public ProgressLogResponse findById(
            Authentication authentication, @PathVariable Long goalId, @PathVariable Long logId) {
        return logService.findById(authentication.getName(), goalId, logId);
    }

    @PutMapping(path = "/{logId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProgressLogResponse update(
            Authentication authentication,
            @PathVariable Long goalId,
            @PathVariable Long logId,
            @Valid @RequestBody ProgressLogRequest request) {
        return logService.update(authentication.getName(), goalId, logId, request);
    }

    @DeleteMapping("/{logId}")
    public ResponseEntity<Void> delete(
            Authentication authentication, @PathVariable Long goalId, @PathVariable Long logId) {
        logService.delete(authentication.getName(), goalId, logId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
