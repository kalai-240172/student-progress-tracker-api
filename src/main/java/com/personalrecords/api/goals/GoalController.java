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
@RequestMapping(path = "/api/goals", produces = MediaType.APPLICATION_JSON_VALUE)
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GoalResponse> create(
            Authentication authentication,
            @Valid @RequestBody GoalRequest request) {
        GoalResponse response = goalService.create(authentication.getName(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<GoalResponse> findAll(Authentication authentication) {
        return goalService.findAll(authentication.getName());
    }

    @GetMapping("/{id}")
    public GoalResponse findById(Authentication authentication, @PathVariable Long id) {
        return goalService.findById(authentication.getName(), id);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public GoalResponse update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody GoalRequest request) {
        return goalService.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        goalService.delete(authentication.getName(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{id}/progress")
    public ProgressSummaryResponse summarize(Authentication authentication, @PathVariable Long id) {
        return goalService.summarize(authentication.getName(), id);
    }
}
