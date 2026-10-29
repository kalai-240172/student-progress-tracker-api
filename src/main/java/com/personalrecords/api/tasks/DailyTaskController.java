package com.personalrecords.api.tasks;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(path = "/api/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
public class DailyTaskController {

    private final DailyTaskService taskService;

    public DailyTaskController(DailyTaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DailyTaskResponse> create(
            Authentication authentication,
            @Valid @RequestBody DailyTaskRequest request) {
        DailyTaskResponse response = taskService.create(authentication.getName(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<DailyTaskResponse> findAll(
            Authentication authentication,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return taskService.findAll(authentication.getName(), date, startDate, endDate);
    }

    @GetMapping("/today-summary")
    public DailyTaskSummaryResponse summarizeToday(Authentication authentication) {
        return taskService.summarizeToday(authentication.getName());
    }

    @GetMapping("/overdue")
    public List<DailyTaskResponse> findOverdue(Authentication authentication) {
        return taskService.findOverdue(authentication.getName());
    }

    @GetMapping("/study-streaks")
    public StudyStreakResponse summarizeStreaks(Authentication authentication) {
        return taskService.summarizeStreaks(authentication.getName());
    }

    @GetMapping("/{id}")
    public DailyTaskResponse findById(Authentication authentication, @PathVariable Long id) {
        return taskService.findById(authentication.getName(), id);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DailyTaskResponse update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody DailyTaskRequest request) {
        return taskService.update(authentication.getName(), id, request);
    }

    @PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DailyTaskResponse updateStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody DailyTaskStatusRequest request) {
        return taskService.updateStatus(authentication.getName(), id, request);
    }

    @PatchMapping(path = "/{id}/actual-duration", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DailyTaskResponse updateActualDuration(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody DailyTaskActualDurationRequest request) {
        return taskService.updateActualDuration(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        taskService.delete(authentication.getName(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
