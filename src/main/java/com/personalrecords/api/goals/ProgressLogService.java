package com.personalrecords.api.goals;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressLogService {

    private final GoalService goalService;
    private final ProgressLogRepository logRepository;

    public ProgressLogService(GoalService goalService, ProgressLogRepository logRepository) {
        this.goalService = goalService;
        this.logRepository = logRepository;
    }

    @Transactional
    public ProgressLogResponse create(String authenticatedEmail, Long goalId, ProgressLogRequest request) {
        Goal goal = goalService.findOwnedGoal(authenticatedEmail, goalId);
        ProgressLog log = new ProgressLog(
                request.activityDate(),
                request.timeSpentMinutes(),
                request.summary(),
                request.notes(),
                goal);
        return ProgressLogResponse.from(logRepository.saveAndFlush(log), goalId);
    }

    @Transactional(readOnly = true)
    public List<ProgressLogResponse> findAll(String authenticatedEmail, Long goalId) {
        Goal goal = goalService.findOwnedGoal(authenticatedEmail, goalId);
        return logRepository.findAllByGoal_IdAndGoal_User_IdOrderByActivityDateDescIdDesc(
                        goal.getId(), goal.getUserId())
                .stream()
                .map(log -> ProgressLogResponse.from(log, goalId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgressLogResponse findById(String authenticatedEmail, Long goalId, Long logId) {
        Goal goal = goalService.findOwnedGoal(authenticatedEmail, goalId);
        return ProgressLogResponse.from(findOwnedLog(logId, goalId, goal.getUserId()), goalId);
    }

    @Transactional
    public ProgressLogResponse update(
            String authenticatedEmail, Long goalId, Long logId, ProgressLogRequest request) {
        Goal goal = goalService.findOwnedGoal(authenticatedEmail, goalId);
        ProgressLog log = findOwnedLog(logId, goalId, goal.getUserId());
        log.update(request.activityDate(), request.timeSpentMinutes(), request.summary(), request.notes());
        return ProgressLogResponse.from(logRepository.saveAndFlush(log), goalId);
    }

    @Transactional
    public void delete(String authenticatedEmail, Long goalId, Long logId) {
        Goal goal = goalService.findOwnedGoal(authenticatedEmail, goalId);
        logRepository.delete(findOwnedLog(logId, goalId, goal.getUserId()));
    }

    private ProgressLog findOwnedLog(Long logId, Long goalId, Long userId) {
        return logRepository.findByIdAndGoal_IdAndGoal_User_Id(logId, goalId, userId)
                .orElseThrow(ProgressLogNotFoundException::new);
    }
}
