package com.personalrecords.api.records;

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
@RequestMapping(path = "/api/records", produces = MediaType.APPLICATION_JSON_VALUE)
public class StudentRecordController {

    private final StudentRecordService recordService;

    public StudentRecordController(StudentRecordService recordService) {
        this.recordService = recordService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudentRecordResponse> create(
            Authentication authentication,
            @Valid @RequestBody StudentRecordRequest request) {
        StudentRecordResponse response = recordService.create(authentication.getName(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<StudentRecordResponse> findAll(Authentication authentication) {
        return recordService.findAll(authentication.getName());
    }

    @GetMapping("/{id}")
    public StudentRecordResponse findById(Authentication authentication, @PathVariable Long id) {
        return recordService.findById(authentication.getName(), id);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StudentRecordResponse update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody StudentRecordRequest request) {
        return recordService.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        recordService.delete(authentication.getName(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
