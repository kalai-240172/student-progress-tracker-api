package com.personalrecords.api.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTaskRepository extends JpaRepository<DailyTask, Long> {

    @Query("""
            select task from DailyTask task
            where task.user.id = :userId
              and (:startDate is null or task.plannedDate >= :startDate)
              and (:endDate is null or task.plannedDate <= :endDate)
            order by task.plannedDate, task.id
            """)
    Stream<DailyTask> streamForExport(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    List<DailyTask> findAllByUser_IdOrderByPlannedDateAscIdAsc(Long userId);

    List<DailyTask> findAllByUser_IdAndPlannedDateOrderByPlannedDateAscIdAsc(Long userId, LocalDate plannedDate);

    List<DailyTask> findAllByUser_IdAndPlannedDateBetweenOrderByPlannedDateAscIdAsc(
            Long userId, LocalDate startDate, LocalDate endDate);

    Optional<DailyTask> findByIdAndUser_Id(Long id, Long userId);

    List<DailyTask> findAllByUser_IdAndPlannedDateBeforeAndStatusNotOrderByPlannedDateDescIdAsc(
            Long userId, LocalDate plannedDate, DailyTaskStatus status);

    @Query("""
            select distinct task.plannedDate
            from DailyTask task
            where task.user.id = :userId
              and task.status = :status
              and task.plannedDate <= :asOfDate
            order by task.plannedDate
            """)
    List<LocalDate> findDistinctCompletedDates(
            @Param("userId") Long userId,
            @Param("status") DailyTaskStatus status,
            @Param("asOfDate") LocalDate asOfDate);
}
