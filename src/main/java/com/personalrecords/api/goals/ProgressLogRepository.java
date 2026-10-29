package com.personalrecords.api.goals;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.personalrecords.api.exports.ProgressLogExportRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgressLogRepository extends JpaRepository<ProgressLog, Long> {

    List<ProgressLog> findAllByGoal_IdAndGoal_User_IdOrderByActivityDateDescIdDesc(
            Long goalId, Long userId);

    Optional<ProgressLog> findByIdAndGoal_IdAndGoal_User_Id(Long id, Long goalId, Long userId);

    @Query("""
            select log.id as id,
                   log.goal.id as goalId,
                   log.goal.title as goalTitle,
                   log.activityDate as activityDate,
                   log.timeSpentMinutes as timeSpentMinutes,
                   log.summary as summary,
                   log.notes as notes,
                   log.createdAt as createdAt,
                   log.updatedAt as updatedAt
            from ProgressLog log
            where log.goal.user.id = :userId
              and (:startDate is null or log.activityDate >= :startDate)
              and (:endDate is null or log.activityDate <= :endDate)
            order by log.activityDate, log.id
            """)
    Stream<ProgressLogExportRow> streamForExport(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            select coalesce(sum(log.timeSpentMinutes), 0) as totalRecordedMinutes,
                   count(log.id) as totalLogCount,
                   coalesce(sum(case when log.activityDate >= :weekStart
                                     and log.activityDate < :weekEnd
                                then log.timeSpentMinutes else 0 end), 0) as currentWeekMinutes,
                   coalesce(sum(case when log.activityDate >= :monthStart
                                     and log.activityDate < :monthEnd
                                then log.timeSpentMinutes else 0 end), 0) as currentMonthMinutes,
                   max(log.activityDate) as mostRecentActivityDate
            from ProgressLog log
            where log.goal.id = :goalId
            """)
    ProgressTotals summarize(
            @Param("goalId") Long goalId,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);

    interface ProgressTotals {
        Long getTotalRecordedMinutes();

        Long getTotalLogCount();

        Long getCurrentWeekMinutes();

        Long getCurrentMonthMinutes();

        LocalDate getMostRecentActivityDate();
    }
}
