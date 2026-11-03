package com.personalrecords.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.personalrecords.api.sample.SampleDataInitialization;
import com.personalrecords.api.sample.SampleDataInitializationRepository;
import com.personalrecords.api.tasks.DailyTask;
import com.personalrecords.api.tasks.DailyTaskRepository;
import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sample-data-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SampleDataApiTests {

    private static final String PASSWORD = "StrongPassword123!";
    private static final String OWNER_EMAIL = "sample-owner@example.com";
    private static final String OTHER_EMAIL = "sample-other@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRecordRepository recordRepository;

    @Autowired
    private DailyTaskRepository taskRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private ProgressLogRepository progressLogRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private AcademicSubjectRepository subjectRepository;

    @Autowired
    private SampleDataInitializationRepository initializationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabase() {
        initializationRepository.deleteAll();
        progressLogRepository.deleteAll();
        goalRepository.deleteAll();
        taskRepository.deleteAll();
        recordRepository.deleteAll();
        subjectRepository.deleteAll();
        semesterRepository.deleteAll();
        academicYearRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.saveAndFlush(new User("Sample Owner", OWNER_EMAIL, passwordEncoder.encode(PASSWORD)));
        userRepository.saveAndFlush(new User("Other Student", OTHER_EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    @Test
    void initializesOwnerScopedSampleDataOnceAcrossAllModules() throws Exception {
        mockMvc.perform(post("/api/sample-data/initialize")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        User owner = userRepository.findByNormalizedEmail(OWNER_EMAIL).orElseThrow();
        var records = recordRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(owner.getId());
        var tasks = taskRepository.findAllByUser_IdOrderByPlannedDateAscIdAsc(owner.getId());
        var goals = goalRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(owner.getId());
        var years = academicYearRepository.findAllByUser_IdOrderByStartDateDescIdDesc(owner.getId());

        org.junit.jupiter.api.Assertions.assertEquals(5, records.size());
        org.junit.jupiter.api.Assertions.assertEquals(5, tasks.size());
        org.junit.jupiter.api.Assertions.assertEquals(5, goals.size());
        org.junit.jupiter.api.Assertions.assertEquals(1, years.size());
        org.junit.jupiter.api.Assertions.assertEquals(5, progressLogCount(goals));
        org.junit.jupiter.api.Assertions.assertEquals(5, subjectRepository
                .findAllBySemester_AcademicYear_User_IdOrderByIdAsc(owner.getId()).size());
        org.junit.jupiter.api.Assertions.assertTrue(recordRepository
                .findAllByUser_IdOrderByCreatedAtDescIdDesc(
                        userRepository.findByNormalizedEmail(OTHER_EMAIL).orElseThrow().getId())
                .isEmpty());

        mockMvc.perform(post("/api/sample-data/initialize")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertEquals(5, recordRepository
                .findAllByUser_IdOrderByCreatedAtDescIdDesc(owner.getId()).size());
        org.junit.jupiter.api.Assertions.assertEquals(5, taskRepository
                .findAllByUser_IdOrderByPlannedDateAscIdAsc(owner.getId()).size());
        org.junit.jupiter.api.Assertions.assertEquals(5, goalRepository
                .findAllByUser_IdOrderByCreatedAtDescIdDesc(owner.getId()).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, initializationRepository.count());
    }

    @Test
    void sampleInitializationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/sample-data/initialize").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void initializationPreservesExistingRecordsAndFillsModulesToFive() throws Exception {
        User owner = userRepository.findByNormalizedEmail(OWNER_EMAIL).orElseThrow();
        recordRepository.saveAndFlush(new StudentRecord(
                "Existing portfolio entry",
                "Keep this user-created record unchanged.",
                RecordCategory.PROJECT,
                "Northstar University",
                LocalDate.now().minusMonths(1),
                null,
                RecordStatus.IN_PROGRESS,
                owner));
        taskRepository.saveAndFlush(new DailyTask(
                "Existing user task",
                "Keep this task unchanged.",
                "Physics",
                LocalDate.now(),
                30,
                owner));
        Goal existingGoal = goalRepository.saveAndFlush(new Goal(
                "Existing user goal",
                "Keep this goal and its history unchanged.",
                GoalCategory.STUDY,
                LocalDate.now().plusMonths(1),
                300,
                Set.of(),
                GoalStatus.IN_PROGRESS,
                owner));
        progressLogRepository.saveAndFlush(new ProgressLog(
                LocalDate.now().minusDays(1),
                30,
                "Existing user progress",
                null,
                existingGoal));
        AcademicYear existingYear = academicYearRepository.saveAndFlush(new AcademicYear(
                "2026-2027",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2027, 6, 30),
                owner));
        Semester existingSemester = semesterRepository.saveAndFlush(new Semester(
                "Existing semester",
                LocalDate.now(),
                LocalDate.now().plusMonths(5),
                existingYear));
        subjectRepository.saveAndFlush(new AcademicSubject(
                "Existing user subject",
                "CS101",
                3,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(80),
                OfficialGrade.B,
                existingSemester));
        initializationRepository.saveAndFlush(new SampleDataInitialization(owner));

        mockMvc.perform(post("/api/sample-data/initialize")
                        .with(httpBasic(OWNER_EMAIL, PASSWORD))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        var records = recordRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(owner.getId());
        org.junit.jupiter.api.Assertions.assertEquals(5, records.size());
        org.junit.jupiter.api.Assertions.assertTrue(records.stream()
                .anyMatch(record -> record.getTitle().equals("Existing portfolio entry")));
        var tasks = taskRepository.findAllByUser_IdOrderByPlannedDateAscIdAsc(owner.getId());
        org.junit.jupiter.api.Assertions.assertEquals(5, tasks.size());
        org.junit.jupiter.api.Assertions.assertTrue(tasks.stream()
                .anyMatch(task -> task.getTitle().equals("Existing user task")));
        var goals = goalRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(owner.getId());
        org.junit.jupiter.api.Assertions.assertEquals(5, goals.size());
        org.junit.jupiter.api.Assertions.assertTrue(goals.stream()
                .anyMatch(goal -> goal.getTitle().equals("Existing user goal")));
        org.junit.jupiter.api.Assertions.assertEquals(5, progressLogCount(goals));
        var subjects = subjectRepository.findAllBySemester_AcademicYear_User_IdOrderByIdAsc(owner.getId());
        org.junit.jupiter.api.Assertions.assertEquals(5, subjects.size());
        org.junit.jupiter.api.Assertions.assertTrue(subjects.stream()
                .anyMatch(subject -> subject.getName().equals("Existing user subject")));
    }

    private int progressLogCount(List<Goal> goals) {
        return goals.stream()
                .mapToInt(goal -> progressLogRepository
                        .findAllByGoal_IdAndGoal_User_IdOrderByActivityDateDescIdDesc(
                                goal.getId(), goal.getUserId())
                        .size())
                .sum();
    }
}
