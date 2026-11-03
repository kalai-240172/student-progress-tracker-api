package com.personalrecords.api.focus;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/focus-sessions", produces = "application/json")
public class FocusSessionController {

    private final FocusSessionService focusSessionService;

    public FocusSessionController(FocusSessionService focusSessionService) {
        this.focusSessionService = focusSessionService;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<FocusSessionResponse> create(
            Authentication authentication,
            @Valid @RequestBody FocusSessionRequest request) {
        FocusSessionSaveResult result = focusSessionService.create(authentication.getName(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.session());
    }

    @GetMapping
    public List<FocusSessionResponse> findBetween(
            Authentication authentication,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return focusSessionService.findBetween(authentication.getName(), startDate, endDate);
    }
}
