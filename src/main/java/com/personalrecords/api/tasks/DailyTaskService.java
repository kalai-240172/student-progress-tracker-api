package com.personalrecords.api.tasks;

import java.time.LocalDate;
import java.util.List;

import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyTaskService {

    private final DailyTaskRepository taskRepository;
    private final UserRepository userRepository;

    public DailyTaskService(DailyTaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DailyTaskResponse create(String authenticatedEmail, DailyTaskRequest request) {
        User user = findAuthenticatedUser(authenticatedEmail);
        DailyTask task = new DailyTask(
                request.title(),
                request.description(),
                request.subject(),
                request.plannedDate(),
                request.estimatedDurationMinutes(),
                user);
        return DailyTaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional(readOnly = true)
    public List<DailyTaskResponse> findAll(
            String authenticatedEmail, LocalDate date, LocalDate startDate, LocalDate endDate) {
        User user = findAuthenticatedUser(authenticatedEmail);
        validateDateFilters(date, startDate, endDate);
        List<DailyTask> tasks;
        if (date != null) {
            tasks = taskRepository.findAllByUser_IdAndPlannedDateOrderByPlannedDateAscIdAsc(user.getId(), date);
        } else if (startDate != null) {
            tasks = taskRepository.findAllByUser_IdAndPlannedDateBetweenOrderByPlannedDateAscIdAsc(
                    user.getId(), startDate, endDate);
        } else {
            tasks = taskRepository.findAllByUser_IdOrderByPlannedDateAscIdAsc(user.getId());
        }
        return tasks.stream().map(DailyTaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DailyTaskResponse findById(String authenticatedEmail, Long id) {
        User user = findAuthenticatedUser(authenticatedEmail);
        return DailyTaskResponse.from(findOwnedTask(id, user.getId()));
    }

    @Transactional
    public DailyTaskResponse update(String authenticatedEmail, Long id, DailyTaskRequest request) {
        User user = findAuthenticatedUser(authenticatedEmail);
        DailyTask task = findOwnedTask(id, user.getId());
        task.update(
                request.title(),
                request.description(),
                request.subject(),
                request.plannedDate(),
                request.estimatedDurationMinutes());
        return DailyTaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public DailyTaskResponse updateStatus(String authenticatedEmail, Long id, DailyTaskStatusRequest request) {
        User user = findAuthenticatedUser(authenticatedEmail);
        DailyTask task = findOwnedTask(id, user.getId());
        task.updateStatus(request.status());
        return DailyTaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public DailyTaskResponse updateActualDuration(
            String authenticatedEmail, Long id, DailyTaskActualDurationRequest request) {
        User user = findAuthenticatedUser(authenticatedEmail);
        DailyTask task = findOwnedTask(id, user.getId());
        task.updateActualDuration(request.actualDurationMinutes());
        return DailyTaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public void delete(String authenticatedEmail, Long id) {
        User user = findAuthenticatedUser(authenticatedEmail);
        taskRepository.delete(findOwnedTask(id, user.getId()));
    }

    @Transactional(readOnly = true)
    public DailyTaskSummaryResponse summarizeToday(String authenticatedEmail) {
        User user = findAuthenticatedUser(authenticatedEmail);
        LocalDate today = LocalDate.now();
        List<DailyTask> tasks = taskRepository.findAllByUser_IdAndPlannedDateOrderByPlannedDateAscIdAsc(
                user.getId(), today);
        long completedCount = tasks.stream()
                .filter(task -> task.getStatus() == DailyTaskStatus.COMPLETED)
                .count();
        long actualMinutes = tasks.stream()
                .map(DailyTask::getActualDurationMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        long plannedMinutes = tasks.stream()
                .mapToLong(task -> task.getEstimatedDurationMinutes())
                .sum();
        return new DailyTaskSummaryResponse(
                today,
                tasks.size(),
                completedCount,
                tasks.size() - completedCount,
                plannedMinutes,
                actualMinutes);
    }

    private void validateDateFilters(LocalDate date, LocalDate startDate, LocalDate endDate) {
        if (date != null && (startDate != null || endDate != null)) {
            throw new DailyTaskDateFilterException(
                    "date", "Use either date or the startDate/endDate range, not both");
        }
        if ((startDate == null) != (endDate == null)) {
            throw new DailyTaskDateFilterException(
                    startDate == null ? "startDate" : "endDate",
                    "Both startDate and endDate are required for a date range");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new DailyTaskDateFilterException("startDate", "startDate must be on or before endDate");
        }
    }

    private DailyTask findOwnedTask(Long id, Long userId) {
        return taskRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(DailyTaskNotFoundException::new);
    }

    private User findAuthenticatedUser(String authenticatedEmail) {
        return userRepository.findByNormalizedEmail(authenticatedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user was not found"));
    }
}
