package com.personalrecords.api.focus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;

import com.personalrecords.api.academic.AcademicSubject;
import com.personalrecords.api.academic.AcademicSubjectRepository;
import com.personalrecords.api.goals.Goal;
import com.personalrecords.api.goals.GoalNotFoundException;
import com.personalrecords.api.goals.GoalRepository;
import com.personalrecords.api.goals.ProgressLog;
import com.personalrecords.api.goals.ProgressLogRepository;
import com.personalrecords.api.tasks.DailyTask;
import com.personalrecords.api.tasks.DailyTaskRepository;
import com.personalrecords.api.tasks.DailyTaskStatus;
import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FocusSessionService {

    private static final int MIN_FOCUS_DURATION_MINUTES = 5;
    private static final int MAX_FOCUS_DURATION_MINUTES = 120;
    private static final int FOCUS_DURATION_STEP_MINUTES = 5;

    private final FocusSessionRepository focusSessionRepository;
    private final AcademicSubjectRepository subjectRepository;
    private final GoalRepository goalRepository;
    private final ProgressLogRepository progressLogRepository;
    private final DailyTaskRepository taskRepository;
    private final UserRepository userRepository;

    public FocusSessionService(
            FocusSessionRepository focusSessionRepository,
            AcademicSubjectRepository subjectRepository,
            GoalRepository goalRepository,
            ProgressLogRepository progressLogRepository,
            DailyTaskRepository taskRepository,
            UserRepository userRepository) {
        this.focusSessionRepository = focusSessionRepository;
        this.subjectRepository = subjectRepository;
        this.goalRepository = goalRepository;
        this.progressLogRepository = progressLogRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FocusSessionSaveResult create(String email, FocusSessionRequest request) {
        User user = userRepository.findByEmailForUpdate(User.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user was not found"));
        var existing = focusSessionRepository.findByUser_IdAndRequestKey(user.getId(), request.requestKey());
        if (existing.isPresent()) {
            return new FocusSessionSaveResult(FocusSessionResponse.from(existing.get()), false);
        }

        validateCompletedFocus(request);
        AcademicSubject subject = subjectRepository
                .findByIdAndSemester_AcademicYear_User_Id(request.subjectId(), user.getId())
                .orElseThrow(() -> new FocusSubjectNotFoundException());
        Goal goal = request.goalId() == null
                ? null
                : goalRepository.findByIdAndUser_Id(request.goalId(), user.getId())
                        .orElseThrow(GoalNotFoundException::new);

        String summary = "Completed a " + request.durationMinutes()
                + "-minute focus session for " + subject.getName() + ".";
        LocalDate activityDate = LocalDate.ofInstant(request.completedAt(), java.time.ZoneId.systemDefault());
        if (goal != null) {
            progressLogRepository.save(new ProgressLog(
                    activityDate,
                    request.durationMinutes(),
                    summary,
                    "Tracked in Focus Arena.",
                    goal));
        }

        DailyTask task = new DailyTask(
                "Focus session: " + subject.getName(),
                summary,
                subject.getName(),
                activityDate,
                request.durationMinutes(),
                user);
        task.updateStatus(DailyTaskStatus.COMPLETED);
        task.updateActualDuration(request.durationMinutes());
        taskRepository.save(task);

        FocusSession session = new FocusSession(
                request.requestKey(),
                subject.getName(),
                subject.getId(),
                subject.getCode(),
                goal == null ? null : goal.getId(),
                goal == null ? null : goal.getTitle(),
                request.durationMinutes(),
                request.startedAt(),
                request.completedAt(),
                user);
        FocusSession saved = focusSessionRepository.saveAndFlush(session);
        return new FocusSessionSaveResult(FocusSessionResponse.from(saved), true);
    }

    @Transactional(readOnly = true)
    public List<FocusSessionResponse> findBetween(String email, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new FocusSessionDateFilterException();
        }
        User user = findUser(email);
        Instant start = startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
        return focusSessionRepository
                .findAllByUser_IdAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDescIdDesc(
                        user.getId(), start, end)
                .stream()
                .map(FocusSessionResponse::from)
                .toList();
    }

    private void validateCompletedFocus(FocusSessionRequest request) {
        if (request.requestKey() == null || request.requestKey().isBlank()) {
            throw new FocusSessionValidationException("requestKey", "A session request key is required.");
        }
        if (request.durationMinutes() < MIN_FOCUS_DURATION_MINUTES
                || request.durationMinutes() > MAX_FOCUS_DURATION_MINUTES
                || request.durationMinutes() % FOCUS_DURATION_STEP_MINUTES != 0) {
            throw new FocusSessionValidationException(
                    "durationMinutes", "Focus duration must be from 5 to 120 minutes in 5-minute increments.");
        }
        if (!request.completedAt().isAfter(request.startedAt())) {
            throw new FocusSessionValidationException(
                    "completedAt", "Session completion must be after its start time.");
        }
        if (Duration.between(request.startedAt(), request.completedAt()).toMinutes() < request.durationMinutes()) {
            throw new FocusSessionValidationException(
                    "completedAt", "A focus session must span at least its selected duration before it can be saved.");
        }
        if (request.startedAt().isAfter(Instant.now())) {
            throw new FocusSessionValidationException("startedAt", "Session start cannot be in the future.");
        }
        if (request.completedAt().isAfter(Instant.now())) {
            throw new FocusSessionValidationException(
                    "completedAt", "Session completion cannot be in the future.");
        }
    }

    private User findUser(String email) {
        return userRepository.findByNormalizedEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user was not found"));
    }
}
