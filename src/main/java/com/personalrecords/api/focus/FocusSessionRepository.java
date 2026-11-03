package com.personalrecords.api.focus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {

    Optional<FocusSession> findByUser_IdAndRequestKey(Long userId, String requestKey);

    List<FocusSession> findAllByUser_IdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDescIdDesc(
            Long userId, Instant start, Instant end);
}
