package com.personalrecords.api.goals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private static final BigDecimal MAX_PERCENTAGE = BigDecimal.valueOf(100);

    private final GoalRepository goalRepository;
    private final ProgressLogRepository progressLogRepository;
    private final UserRepository userRepository;

    public GoalService(
            GoalRepository goalRepository,
            ProgressLogRepository progressLogRepository,
            UserRepository userRepository) {
        this.goalRepository = goalRepository;
        this.progressLogRepository = progressLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public GoalResponse create(String authenticatedEmail, GoalRequest request) {
        User user = findAuthenticatedUser(authenticatedEmail);
        Goal goal = new Goal(
                request.title(),
                request.description(),
                request.category(),
                request.targetDate(),
                request.targetDurationMinutes(),
                preferredWeekdays(request),
                request.status(),
                user);
        return GoalResponse.from(goalRepository.saveAndFlush(goal));
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> findAll(String authenticatedEmail) {
        User user = findAuthenticatedUser(authenticatedEmail);
        return goalRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(user.getId())
                .stream()
                .map(GoalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse findById(String authenticatedEmail, Long id) {
        User user = findAuthenticatedUser(authenticatedEmail);
        return GoalResponse.from(findOwnedGoal(id, user.getId()));
    }

    @Transactional
    public GoalResponse update(String authenticatedEmail, Long id, GoalRequest request) {
        User user = findAuthenticatedUser(authenticatedEmail);
        Goal goal = findOwnedGoal(id, user.getId());
        goal.update(
                request.title(),
                request.description(),
                request.category(),
                request.targetDate(),
                request.targetDurationMinutes(),
                preferredWeekdays(request),
                request.status());
        return GoalResponse.from(goalRepository.saveAndFlush(goal));
    }

    @Transactional
    public void delete(String authenticatedEmail, Long id) {
        User user = findAuthenticatedUser(authenticatedEmail);
        Goal goal = findOwnedGoal(id, user.getId());
        goalRepository.delete(goal);
    }

    @Transactional(readOnly = true)
    public ProgressSummaryResponse summarize(String authenticatedEmail, Long id) {
        User user = findAuthenticatedUser(authenticatedEmail);
        Goal goal = findOwnedGoal(id, user.getId());

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);
        ProgressLogRepository.ProgressTotals totals = progressLogRepository.summarize(
                id, weekStart, weekStart.plusWeeks(1), monthStart, monthStart.plusMonths(1));

        long totalMinutes = totals.getTotalRecordedMinutes();
        long targetMinutes = goal.getTargetDurationMinutes();
        BigDecimal percentage = BigDecimal.valueOf(totalMinutes)
                .multiply(MAX_PERCENTAGE)
                .divide(BigDecimal.valueOf(targetMinutes), 2, RoundingMode.HALF_UP)
                .min(MAX_PERCENTAGE);

        return new ProgressSummaryResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getTargetDurationMinutes(),
                totalMinutes,
                Math.max(0, targetMinutes - totalMinutes),
                percentage,
                totals.getTotalLogCount(),
                totals.getCurrentWeekMinutes(),
                totals.getCurrentMonthMinutes(),
                totals.getMostRecentActivityDate());
    }

    Goal findOwnedGoal(String authenticatedEmail, Long goalId) {
        User user = findAuthenticatedUser(authenticatedEmail);
        return findOwnedGoal(goalId, user.getId());
    }

    private Goal findOwnedGoal(Long id, Long userId) {
        return goalRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(GoalNotFoundException::new);
    }

    private User findAuthenticatedUser(String authenticatedEmail) {
        return userRepository.findByNormalizedEmail(authenticatedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user was not found"));
    }

    private Set<DayOfWeek> preferredWeekdays(GoalRequest request) {
        return request.preferredWeekdays() == null ? Set.of() : request.preferredWeekdays();
    }
}
