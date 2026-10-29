package com.personalrecords.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import com.personalrecords.api.academic.AcademicSubjectRepository;
import com.personalrecords.api.academic.AcademicYear;
import com.personalrecords.api.academic.AcademicYearRepository;
import com.personalrecords.api.academic.Semester;
import com.personalrecords.api.academic.SemesterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AcademicProgressApiTests {

    private static final String PASSWORD = "StrongPassword123!";
    private String ownerEmail;
    private String otherEmail;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademicYearRepository yearRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private AcademicSubjectRepository subjectRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void createUsers() {
        String suffix = UUID.randomUUID().toString();
        ownerEmail = "academic-owner-" + suffix + "@example.com";
        otherEmail = "academic-other-" + suffix + "@example.com";
        userRepository.saveAndFlush(new User("Academic Owner", ownerEmail, passwordEncoder.encode(PASSWORD)));
        userRepository.saveAndFlush(new User("Other User", otherEmail, passwordEncoder.encode(PASSWORD)));
    }

    @AfterEach
    void removeTestAcademicDataAndUsers() {
        User owner = userRepository.findByNormalizedEmail(ownerEmail).orElseThrow();
        yearRepository.findAllByUser_IdOrderByStartDateDescIdDesc(owner.getId()).forEach(year -> {
            var semesters = semesterRepository
                    .findAllByAcademicYear_IdAndAcademicYear_User_IdOrderByStartDateAscIdAsc(year.getId(), owner.getId());
            semesters.forEach(semester -> subjectRepository.deleteAll(
                    subjectRepository.findAllBySemester_IdAndSemester_AcademicYear_User_IdOrderByIdAsc(
                            semester.getId(), owner.getId())));
            semesterRepository.deleteAll(semesters);
        });
        yearRepository.deleteAll(yearRepository.findAllByUser_IdOrderByStartDateDescIdDesc(owner.getId()));
        userRepository.delete(owner);
        userRepository.findByNormalizedEmail(otherEmail).ifPresent(userRepository::delete);
    }

    @Test
    void academicYearsAndSemestersSupportOwnerScopedCrudAndProtectParentDeletion() throws Exception {
        long yearId = createYear(ownerEmail, "2026-2027");
        long semesterId = createSemester(ownerEmail, yearId, "Semester 1");

        mockMvc.perform(get("/api/academic-years").with(httpBasic(ownerEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label").value("2026-2027"))
                .andExpect(jsonPath("$[0].user").doesNotExist());
        mockMvc.perform(get("/api/academic-years/{id}", yearId).with(httpBasic(ownerEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(yearId));
        mockMvc.perform(put("/api/academic-years/{id}", yearId)
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf())
                        .contentType("application/json")
                        .content("{\"label\":\"2026-2028\",\"startDate\":\"2026-01-01\",\"endDate\":\"2027-12-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("2026-2028"));
        mockMvc.perform(get("/api/academic-years/{id}/semesters", yearId).with(httpBasic(ownerEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(semesterId))
                .andExpect(jsonPath("$[0].academicYearId").value(yearId));
        mockMvc.perform(put("/api/semesters/{id}", semesterId)
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf())
                        .contentType("application/json")
                        .content("{\"label\":\"Semester 1 updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Semester 1 updated"));
        mockMvc.perform(delete("/api/academic-years/{id}", yearId)
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/semesters/{id}", semesterId)
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/academic-years/{id}", yearId)
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void subjectCrudValidatesMarksAndCalculatesPercentageWithoutExposingOwner() throws Exception {
        long yearId = createYear(ownerEmail, "2025-2026");
        long semesterId = createSemester(ownerEmail, yearId, "Semester 2");
        long subjectId = createSubject(semesterId,
                "{\"name\":\"Data Structures\",\"code\":\"CS201\",\"credits\":4,"
                        + "\"maximumMarks\":100,\"obtainedMarks\":81.25,\"officialGrade\":\"A+\"}");

        mockMvc.perform(get("/api/semesters/{id}/subjects", semesterId).with(httpBasic(ownerEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Data Structures"))
                .andExpect(jsonPath("$[0].percentage").value(81.25))
                .andExpect(jsonPath("$[0].gradePoint").value(9))
                .andExpect(jsonPath("$[0].user").doesNotExist());
        mockMvc.perform(put("/api/subjects/{id}", subjectId)
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Data Structures II\",\"code\":\"CS202\",\"credits\":3,"
                                + "\"maximumMarks\":50,\"obtainedMarks\":null,\"officialGrade\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Data Structures II"))
                .andExpect(jsonPath("$.percentage").doesNotExist())
                .andExpect(jsonPath("$.gradePoint").doesNotExist());
        mockMvc.perform(get("/api/subjects/{id}", subjectId).with(httpBasic(ownerEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officialGrade").doesNotExist());
        mockMvc.perform(delete("/api/semesters/{id}", semesterId)
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/subjects/{id}", subjectId)
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/semesters/{id}", semesterId)
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsInvalidAcademicDatesSubjectsMarksAndUnsupportedGrades() throws Exception {
        mockMvc.perform(post("/api/academic-years")
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf())
                        .contentType("application/json")
                        .content("{\"label\":\"  \",\"startDate\":\"2027-01-01\",\"endDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.label").exists())
                .andExpect(jsonPath("$.fieldErrors.dateRange").exists());
        long yearId = createYear(ownerEmail, "2026-2027");
        long semesterId = createSemester(ownerEmail, yearId, "Semester 1");
        String[] invalidSubjects = {
                "{\"name\":\" \",\"credits\":1,\"maximumMarks\":100}",
                "{\"name\":\"Zero credits\",\"credits\":0,\"maximumMarks\":100}",
                "{\"name\":\"Zero max\",\"credits\":1,\"maximumMarks\":0}",
                "{\"name\":\"Negative marks\",\"credits\":1,\"maximumMarks\":100,\"obtainedMarks\":-1}",
                "{\"name\":\"Too many marks\",\"credits\":1,\"maximumMarks\":100,\"obtainedMarks\":101}",
                "{\"name\":\"Bad grade\",\"credits\":1,\"maximumMarks\":100,\"officialGrade\":\"D\"}",
                "{\"name\":\"Grade point override\",\"credits\":1,\"maximumMarks\":100,\"officialGrade\":\"O\",\"gradePoint\":1}",
                "{\"name\":\"Excess precision\",\"credits\":1,\"maximumMarks\":100,\"obtainedMarks\":12.345}"
        };
        for (String request : invalidSubjects) {
            mockMvc.perform(post("/api/semesters/{id}/subjects", semesterId)
                            .with(httpBasic(ownerEmail, PASSWORD)).with(csrf())
                            .contentType("application/json").content(request))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void exactCreditWeightedGpaExcludesUAndMissingGrades() throws Exception {
        long yearId = createYear(ownerEmail, "2026-2027");
        long semesterId = createSemester(ownerEmail, yearId, "Semester 1");
        createSubject(semesterId, subject("Java", 4, "O"));
        createSubject(semesterId, subject("DBMS", 3, "A+"));
        createSubject(semesterId, subject("Networks", 3, "A"));
        createSubject(semesterId, subject("Mathematics", 4, "B+"));
        createSubject(semesterId, subject("English", 2, "B"));
        createSubject(semesterId, subject("Failed elective", 5, "U"));
        createSubject(semesterId, "{\"name\":\"Awaiting result\",\"credits\":6,\"maximumMarks\":100}");

        mockMvc.perform(get("/api/semesters/{id}/summary", semesterId)
                        .with(httpBasic(ownerEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubjects").value(7))
                .andExpect(jsonPath("$.eligibleSubjects").value(5))
                .andExpect(jsonPath("$.eligibleCredits").value(16))
                .andExpect(jsonPath("$.weightedGradePoints").value(131))
                .andExpect(jsonPath("$.gpa").value(8.19))
                .andExpect(jsonPath("$.subjects", hasSize(7)))
                .andExpect(jsonPath("$.subjects[5].gradePoint").value(0))
                .andExpect(jsonPath("$.subjects[6].gradePoint").doesNotExist());
        mockMvc.perform(get("/api/academic-years/{id}/summary", yearId)
                        .with(httpBasic(ownerEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cgpa").value(8.19));
    }

    @Test
    void cumulativeSummaryCombinesSemestersAndEmptyGpaIsUnavailable() throws Exception {
        long yearId = createYear(ownerEmail, "2026-2027");
        long first = createSemester(ownerEmail, yearId, "Semester 1");
        long second = createSemester(ownerEmail, yearId, "Semester 2");
        mockMvc.perform(get("/api/semesters/{id}/summary", first).with(httpBasic(ownerEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gpa").doesNotExist())
                .andExpect(jsonPath("$.eligibleCredits").value(0));
        createSubject(first, subject("Passed", 2, "O"));
        createSubject(second, subject("Cleared", 3, "C"));
        createSubject(second, subject("Failed", 7, "U"));
        mockMvc.perform(get("/api/academic-summary").with(httpBasic(ownerEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubjects").value(3))
                .andExpect(jsonPath("$.eligibleCredits").value(5))
                .andExpect(jsonPath("$.cgpa").value(7.0))
                .andExpect(jsonPath("$.academicYears", hasSize(1)))
                .andExpect(jsonPath("$.academicYears[0].semesters", hasSize(2)));
    }

    @Test
    void ownershipAndAuthenticationProtectAllAcademicResources() throws Exception {
        long yearId = createYear(ownerEmail, "2026-2027");
        long semesterId = createSemester(ownerEmail, yearId, "Semester 1");
        long subjectId = createSubject(semesterId, subject("Private", 3, "A"));

        mockMvc.perform(get("/api/academic-years").with(httpBasic(otherEmail, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/academic-years/{id}", yearId).with(httpBasic(otherEmail, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/academic-years/{id}", yearId)
                        .with(httpBasic(otherEmail, PASSWORD)).with(csrf())
                        .contentType("application/json").content("{\"label\":\"Changed\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/academic-years/{id}/semesters", yearId).with(httpBasic(otherEmail, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/semesters/{id}", semesterId).with(httpBasic(otherEmail, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/semesters/{id}", semesterId)
                        .with(httpBasic(otherEmail, PASSWORD)).with(csrf())
                        .contentType("application/json").content("{\"label\":\"Changed\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/semesters/{id}/subjects", semesterId).with(httpBasic(otherEmail, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/subjects/{id}", subjectId).with(httpBasic(otherEmail, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/semesters/{id}/summary", semesterId).with(httpBasic(otherEmail, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/academic-years/{id}/summary", yearId).with(httpBasic(otherEmail, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/subjects/{id}", subjectId)
                        .with(httpBasic(otherEmail, PASSWORD)).with(csrf())
                        .contentType("application/json").content(subject("Hijacked", 1, "O")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/academic-years/{id}", yearId)
                        .with(httpBasic(otherEmail, PASSWORD)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/subjects/{id}", subjectId)
                        .with(httpBasic(otherEmail, PASSWORD)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/academic-summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/academic-summary").with(httpBasic(otherEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubjects").value(0))
                .andExpect(jsonPath("$.academicYears", hasSize(0)));
        mockMvc.perform(post("/api/academic-years")
                        .with(httpBasic(ownerEmail, PASSWORD))
                        .contentType("application/json").content("{\"label\":\"No CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void subjectCodesAreUniqueWithinSemesterAndSupportedGradesMapExactly() throws Exception {
        long yearId = createYear(ownerEmail, "2026-2027");
        long semesterId = createSemester(ownerEmail, yearId, "Semester 1");
        String[] grades = {"O", "A+", "A", "B+", "B", "C", "U"};
        int[] points = {10, 9, 8, 7, 6, 5, 0};
        for (int i = 0; i < grades.length; i++) {
            long id = createSubject(semesterId,
                    "{\"name\":\"Subject " + i + "\",\"code\":\"G" + i
                            + "\",\"credits\":1,\"maximumMarks\":100,\"officialGrade\":\""
                            + grades[i] + "\"}");
            mockMvc.perform(get("/api/subjects/{id}", id).with(httpBasic(ownerEmail, PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.officialGrade").value(grades[i]))
                    .andExpect(jsonPath("$.gradePoint").value(points[i]));
        }
        mockMvc.perform(post("/api/semesters/{id}/subjects", semesterId)
                        .with(httpBasic(ownerEmail, PASSWORD)).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Duplicate\",\"code\":\"g0\",\"credits\":1,\"maximumMarks\":100}"))
                .andExpect(status().isConflict());
    }

    private long createYear(String email, String label) throws Exception {
        return create("/api/academic-years", email,
                "{\"label\":\"" + label + "\",\"startDate\":\"2026-01-01\",\"endDate\":\"2027-12-31\"}");
    }

    private long createSemester(String email, long yearId, String label) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/academic-years/{id}/semesters", yearId)
                        .with(httpBasic(email, PASSWORD)).with(csrf())
                        .contentType("application/json")
                        .content("{\"label\":\"" + label + "\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-06-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createSubject(long semesterId, String request) throws Exception {
        return create("/api/semesters/" + semesterId + "/subjects", ownerEmail, request);
    }

    private long create(String path, String email, String request) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .with(httpBasic(email, PASSWORD)).with(csrf())
                        .contentType("application/json").content(request))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String subject(String name, int credits, String grade) {
        return "{\"name\":\"" + name + "\",\"credits\":" + credits
                + ",\"maximumMarks\":100,\"obtainedMarks\":75,\"officialGrade\":\"" + grade + "\"}";
    }
}
