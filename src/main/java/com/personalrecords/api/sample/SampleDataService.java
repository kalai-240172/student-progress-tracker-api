package com.personalrecords.api.sample;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.personalrecords.api.academic.AcademicSubject;
import com.personalrecords.api.academic.AcademicSubjectRepository;
import com.personalrecords.api.academic.AcademicYear;
import com.personalrecords.api.academic.AcademicYearRepository;
import com.personalrecords.api.academic.OfficialGrade;
import com.personalrecords.api.academic.Semester;
import com.personalrecords.api.academic.SemesterRepository;
import com.personalrecords.api.goals.Goal;
import com.personalrecords.api.goals.GoalCategory;
import com.personalrecords.api.goals.GoalRepository;
import com.personalrecords.api.goals.GoalStatus;
import com.personalrecords.api.goals.ProgressLog;
import com.personalrecords.api.goals.ProgressLogRepository;
import com.personalrecords.api.records.RecordCategory;
import com.personalrecords.api.records.RecordStatus;
import com.personalrecords.api.records.StudentRecord;
import com.personalrecords.api.records.StudentRecordRepository;
import com.personalrecords.api.tasks.DailyTask;
import com.personalrecords.api.tasks.DailyTaskRepository;
import com.personalrecords.api.tasks.DailyTaskStatus;
import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleDataService {

    private final UserRepository userRepository;
    private final SampleDataInitializationRepository initializationRepository;
    private final StudentRecordRepository recordRepository;
    private final DailyTaskRepository taskRepository;
    private final GoalRepository goalRepository;
    private final ProgressLogRepository progressLogRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SemesterRepository semesterRepository;
    private final AcademicSubjectRepository academicSubjectRepository;

    public SampleDataService(
            UserRepository userRepository,
            SampleDataInitializationRepository initializationRepository,
            StudentRecordRepository recordRepository,
            DailyTaskRepository taskRepository,
            GoalRepository goalRepository,
            ProgressLogRepository progressLogRepository,
            AcademicYearRepository academicYearRepository,
            SemesterRepository semesterRepository,
            AcademicSubjectRepository academicSubjectRepository) {
        this.userRepository = userRepository;
        this.initializationRepository = initializationRepository;
        this.recordRepository = recordRepository;
        this.taskRepository = taskRepository;
        this.goalRepository = goalRepository;
        this.progressLogRepository = progressLogRepository;
        this.academicYearRepository = academicYearRepository;
        this.semesterRepository = semesterRepository;
        this.academicSubjectRepository = academicSubjectRepository;
    }

    @Transactional
    public void initializeFor(String email) {
        User user = userRepository.findByEmailForUpdate(User.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        seedRecords(user);
        seedTasks(user);
        List<Goal> goals = seedGoals(user);
        seedProgressLogs(goals);
        seedAcademicProgress(user);
        if (!initializationRepository.existsByUser_Id(user.getId())) {
            initializationRepository.save(new SampleDataInitialization(user));
        }
    }

    private void seedRecords(User user) {
        int existingCount = recordRepository
                .findAllByUser_IdOrderByCreatedAtDescIdDesc(user.getId()).size();
        if (existingCount >= 5) {
            return;
        }

        LocalDate today = LocalDate.now();
        List<StudentRecord> samples = List.of(
                new StudentRecord(
                        "Foundations of Computer Science",
                        "Completed core coursework in algorithms, programming, and systems.",
                        RecordCategory.ACADEMIC,
                        "Northstar University",
                        today.minusMonths(14),
                        today.minusMonths(8),
                        RecordStatus.COMPLETED,
                        user),
                new StudentRecord(
                        "Java Programming Certificate",
                        "Completed an assessed course in object-oriented Java and testing.",
                        RecordCategory.CERTIFICATE,
                        "Open Learning Institute",
                        today.minusMonths(7),
                        today.minusMonths(5),
                        RecordStatus.COMPLETED,
                        user),
                new StudentRecord(
                        "Campus Library Study Planner",
                        "Building a small application to organize study sessions and reading lists.",
                        RecordCategory.PROJECT,
                        "Northstar University",
                        today.minusMonths(2),
                        null,
                        RecordStatus.IN_PROGRESS,
                        user),
                new StudentRecord(
                        "Regional Mathematics Challenge",
                        "Selected to represent the department in the annual problem-solving event.",
                        RecordCategory.ACHIEVEMENT,
                        "Northstar University",
                        today.minusMonths(4),
                        today.minusMonths(4),
                        RecordStatus.COMPLETED,
                        user),
                new StudentRecord(
                        "Cloud Fundamentals Workshop",
                        "Upcoming workshop covering cloud architecture and deployment basics.",
                        RecordCategory.CERTIFICATE,
                        "Digital Skills Academy",
                        today.plusWeeks(2),
                        null,
                        RecordStatus.PLANNED,
                        user));
        recordRepository.saveAll(samples.subList(0, Math.min(5 - existingCount, samples.size())));
    }

    private void seedTasks(User user) {
        int existingCount = taskRepository
                .findAllByUser_IdOrderByPlannedDateAscIdAsc(user.getId()).size();
        if (existingCount >= 5) {
            return;
        }

        LocalDate today = LocalDate.now();
        DailyTask calculus = new DailyTask(
                "Review derivatives and practice problems",
                "Work through the mixed review set and check each solution.",
                "Calculus",
                today.minusDays(1),
                60,
                user);
        calculus.updateStatus(DailyTaskStatus.COMPLETED);
        calculus.updateActualDuration(55);

        DailyTask database = new DailyTask(
                "Build a REST endpoint for the course project",
                "Implement request validation and write a short integration test.",
                "Database Systems",
                today,
                50,
                user);
        database.updateStatus(DailyTaskStatus.COMPLETED);
        database.updateActualDuration(45);

        DailyTask biology = new DailyTask(
                "Summarize cell division lecture notes",
                "Create a one-page comparison of mitosis and meiosis.",
                "Biology",
                today,
                35,
                user);
        biology.updateStatus(DailyTaskStatus.IN_PROGRESS);
        biology.updateActualDuration(20);

        List<DailyTask> samples = List.of(
                calculus,
                database,
                biology,
                new DailyTask(
                        "Draft the literature review outline",
                        "Group three sources by theme and note the main evidence.",
                        "English Literature",
                        today.plusDays(1),
                        45,
                        user),
                new DailyTask(
                        "Revise relational algebra exercises",
                        "Practice selection, projection, joins, and set operations.",
                        "Database Systems",
                        today.plusDays(2),
                        40,
                        user));
        taskRepository.saveAll(samples.subList(0, Math.min(5 - existingCount, samples.size())));
    }

    private List<Goal> seedGoals(User user) {
        List<Goal> goals = goalRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(user.getId());
        if (goals.size() >= 5) {
            return goals;
        }

        LocalDate targetDate = LocalDate.now().plusMonths(3);
        List<Goal> samples = List.of(
                new Goal(
                        "Master data structures",
                        "Build confidence with common structures, complexity, and implementation practice.",
                        GoalCategory.STUDY,
                        targetDate,
                        1200,
                        EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                        GoalStatus.IN_PROGRESS,
                        user),
                new Goal(
                        "Complete the database systems project",
                        "Design a normalized schema and deliver a tested REST API.",
                        GoalCategory.PROJECT,
                        targetDate,
                        900,
                        EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                        GoalStatus.IN_PROGRESS,
                        user),
                new Goal(
                        "Strengthen calculus foundations",
                        "Review limits, derivatives, and integral applications each week.",
                        GoalCategory.STUDY,
                        targetDate,
                        600,
                        EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                        GoalStatus.IN_PROGRESS,
                        user),
                new Goal(
                        "Prepare for a cloud fundamentals certificate",
                        "Study core cloud services, security, and deployment concepts.",
                        GoalCategory.CAREER,
                        LocalDate.now().plusMonths(4),
                        480,
                        Set.of(DayOfWeek.SATURDAY),
                        GoalStatus.NOT_STARTED,
                        user),
                new Goal(
                        "Finish the research reading challenge",
                        "Read and annotate three papers on accessible learning technology.",
                        GoalCategory.PERSONAL,
                        LocalDate.now().minusDays(1),
                        180,
                        Set.of(),
                        GoalStatus.COMPLETED,
                        user));
        List<Goal> saved = goalRepository.saveAll(
                samples.subList(0, Math.min(5 - goals.size(), samples.size())));
        List<Goal> allGoals = new ArrayList<>(goals);
        allGoals.addAll(saved);
        return allGoals;
    }

    private void seedProgressLogs(List<Goal> goals) {
        if (goals.isEmpty()) {
            return;
        }
        int existingCount = goals.stream()
                .mapToInt(goal -> progressLogRepository
                        .findAllByGoal_IdAndGoal_User_IdOrderByActivityDateDescIdDesc(
                                goal.getId(), goal.getUserId())
                        .size())
                .sum();
        if (existingCount >= 5) {
            return;
        }

        LocalDate today = LocalDate.now();
        List<ProgressLog> samples = List.of(
                new ProgressLog(
                        today,
                        50,
                        "Reviewed arrays and solved two pointer exercises.",
                        "Revisited the time-complexity notes before practicing.",
                        goals.get(0)),
                new ProgressLog(
                        today.minusDays(1),
                        60,
                        "Implemented and tested a set of linked-list operations.",
                        "Added edge cases for empty and single-item lists.",
                        goals.get(0)),
                new ProgressLog(
                        today.minusDays(2),
                        45,
                        "Designed the project tables and checked their relationships.",
                        "The revised schema now avoids duplicated course details.",
                        goals.get(Math.min(1, goals.size() - 1))),
                new ProgressLog(
                        today.minusDays(3),
                        75,
                        "Practiced derivative rules and checked the worked examples.",
                        "Focused on chain-rule problems.",
                        goals.get(Math.min(2, goals.size() - 1))),
                new ProgressLog(
                        today.minusDays(4),
                        180,
                        "Finished reading and annotating the three research papers.",
                        "Captured key findings and follow-up questions.",
                        goals.get(Math.min(4, goals.size() - 1))));
        progressLogRepository.saveAll(samples.subList(0, Math.min(5 - existingCount, samples.size())));
    }

    private void seedAcademicProgress(User user) {
        List<AcademicYear> years = academicYearRepository
                .findAllByUser_IdOrderByStartDateDescIdDesc(user.getId());
        AcademicYear year;
        if (years.isEmpty()) {
            LocalDate today = LocalDate.now();
            int startYear = today.getMonthValue() >= Month.JULY.getValue()
                    ? today.getYear()
                    : today.getYear() - 1;
            LocalDate yearStart = LocalDate.of(startYear, Month.JULY, 1);
            LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);
            year = academicYearRepository.save(new AcademicYear(
                    startYear + "-" + (startYear + 1), yearStart, yearEnd, user));
        } else {
            year = years.get(0);
        }

        List<Semester> semesters = semesterRepository
                .findAllByAcademicYear_IdAndAcademicYear_User_IdOrderByStartDateAscIdAsc(
                        year.getId(), user.getId());
        Semester semester;
        if (semesters.isEmpty()) {
            LocalDate start = year.getStartDate() == null
                    ? LocalDate.now()
                    : year.getStartDate();
            LocalDate end = start.plusMonths(5);
            semester = semesterRepository.save(new Semester("Semester 1", start, end, year));
        } else {
            semester = semesters.get(0);
        }

        List<AcademicSubject> existingSubjects = academicSubjectRepository
                .findAllBySemester_AcademicYear_User_IdOrderByIdAsc(user.getId());
        if (existingSubjects.size() >= 5) {
            return;
        }

        List<AcademicSubject> samples = List.of(
                sampleSubject("Data Structures", "CS201", 4, 94, OfficialGrade.O, semester),
                sampleSubject("Database Systems", "CS214", 4, 88, OfficialGrade.A_PLUS, semester),
                sampleSubject("Engineering Mathematics", "MA202", 4, 82, OfficialGrade.A, semester),
                sampleSubject("Cell Biology", "BI203", 3, 76, OfficialGrade.B_PLUS, semester),
                sampleSubject("Technical Communication", "EN210", 2, 68, OfficialGrade.B, semester));
        List<String> existingCodes = existingSubjects.stream()
                .map(AcademicSubject::getCode)
                .filter(code -> code != null)
                .toList();
        List<String> existingNames = existingSubjects.stream()
                .map(AcademicSubject::getName)
                .toList();
        List<AcademicSubject> missingSamples = samples.stream()
                .filter(subject -> !existingCodes.contains(subject.getCode()))
                .filter(subject -> !existingNames.contains(subject.getName()))
                .limit(5 - existingSubjects.size())
                .toList();
        academicSubjectRepository.saveAll(missingSamples);
    }

    private AcademicSubject sampleSubject(
            String name,
            String code,
            int credits,
            int obtainedMarks,
            OfficialGrade grade,
            Semester semester) {
        return new AcademicSubject(
                name,
                code,
                credits,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(obtainedMarks),
                grade,
                semester);
    }
}
