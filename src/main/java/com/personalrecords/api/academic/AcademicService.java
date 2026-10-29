package com.personalrecords.api.academic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicService {

    private final AcademicYearRepository yearRepository;
    private final SemesterRepository semesterRepository;
    private final AcademicSubjectRepository subjectRepository;
    private final UserRepository userRepository;

    public AcademicService(
            AcademicYearRepository yearRepository,
            SemesterRepository semesterRepository,
            AcademicSubjectRepository subjectRepository,
            UserRepository userRepository) {
        this.yearRepository = yearRepository;
        this.semesterRepository = semesterRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AcademicYearResponse createYear(String email, AcademicYearRequest request) {
        User user = findUser(email);
        AcademicYear year = new AcademicYear(
                request.label().trim(), request.startDate(), request.endDate(), user);
        return AcademicYearResponse.from(yearRepository.saveAndFlush(year));
    }

    @Transactional(readOnly = true)
    public List<AcademicYearResponse> listYears(String email) {
        User user = findUser(email);
        return yearRepository.findAllByUser_IdOrderByStartDateDescIdDesc(user.getId())
                .stream().map(AcademicYearResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AcademicYearResponse getYear(String email, Long id) {
        return AcademicYearResponse.from(findOwnedYear(id, findUser(email).getId()));
    }

    @Transactional
    public AcademicYearResponse updateYear(String email, Long id, AcademicYearRequest request) {
        AcademicYear year = findOwnedYear(id, findUser(email).getId());
        year.update(request.label().trim(), request.startDate(), request.endDate());
        return AcademicYearResponse.from(yearRepository.saveAndFlush(year));
    }

    @Transactional
    public void deleteYear(String email, Long id) {
        User user = findUser(email);
        AcademicYear year = findOwnedYear(id, user.getId());
        if (semesterRepository.existsByAcademicYear_IdAndAcademicYear_User_Id(id, user.getId())) {
            throw new AcademicConflictException("Delete or move this academic year's semesters first.");
        }
        yearRepository.delete(year);
    }

    @Transactional
    public SemesterResponse createSemester(String email, Long yearId, SemesterRequest request) {
        User user = findUser(email);
        AcademicYear year = findOwnedYear(yearId, user.getId());
        Semester semester = new Semester(
                request.label().trim(), request.startDate(), request.endDate(), year);
        return SemesterResponse.from(semesterRepository.saveAndFlush(semester));
    }

    @Transactional(readOnly = true)
    public List<SemesterResponse> listSemesters(String email, Long yearId) {
        User user = findUser(email);
        findOwnedYear(yearId, user.getId());
        return semesterRepository
                .findAllByAcademicYear_IdAndAcademicYear_User_IdOrderByStartDateAscIdAsc(yearId, user.getId())
                .stream().map(SemesterResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SemesterResponse getSemester(String email, Long id) {
        User user = findUser(email);
        return SemesterResponse.from(findOwnedSemester(id, user.getId()));
    }

    @Transactional
    public SemesterResponse updateSemester(String email, Long id, SemesterRequest request) {
        User user = findUser(email);
        Semester semester = findOwnedSemester(id, user.getId());
        semester.update(request.label().trim(), request.startDate(), request.endDate());
        return SemesterResponse.from(semesterRepository.saveAndFlush(semester));
    }

    @Transactional
    public void deleteSemester(String email, Long id) {
        User user = findUser(email);
        Semester semester = findOwnedSemester(id, user.getId());
        if (subjectRepository.existsBySemester_Id(id)) {
            throw new AcademicConflictException("Delete this semester's subjects before deleting the semester.");
        }
        semesterRepository.delete(semester);
    }

    @Transactional
    public AcademicSubjectResponse createSubject(String email, Long semesterId, AcademicSubjectRequest request) {
        User user = findUser(email);
        Semester semester = findOwnedSemester(semesterId, user.getId());
        String code = normalizeCode(request.code());
        ensureCodeAvailable(semesterId, code, null);
        AcademicSubject subject = new AcademicSubject(
                request.name().trim(),
                code,
                request.credits(),
                request.maximumMarks(),
                request.obtainedMarks(),
                request.officialGrade(),
                semester);
        return AcademicSubjectResponse.from(subjectRepository.saveAndFlush(subject));
    }

    @Transactional(readOnly = true)
    public List<AcademicSubjectResponse> listSubjects(String email, Long semesterId) {
        User user = findUser(email);
        findOwnedSemester(semesterId, user.getId());
        return subjectRepository
                .findAllBySemester_IdAndSemester_AcademicYear_User_IdOrderByIdAsc(semesterId, user.getId())
                .stream().map(AcademicSubjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AcademicSubjectResponse getSubject(String email, Long id) {
        User user = findUser(email);
        return AcademicSubjectResponse.from(findOwnedSubject(id, user.getId()));
    }

    @Transactional
    public AcademicSubjectResponse updateSubject(String email, Long id, AcademicSubjectRequest request) {
        User user = findUser(email);
        AcademicSubject subject = findOwnedSubject(id, user.getId());
        String code = normalizeCode(request.code());
        ensureCodeAvailable(subject.getSemester().getId(), code, id);
        subject.update(
                request.name().trim(),
                code,
                request.credits(),
                request.maximumMarks(),
                request.obtainedMarks(),
                request.officialGrade());
        return AcademicSubjectResponse.from(subjectRepository.saveAndFlush(subject));
    }

    @Transactional
    public void deleteSubject(String email, Long id) {
        User user = findUser(email);
        subjectRepository.delete(findOwnedSubject(id, user.getId()));
    }

    @Transactional(readOnly = true)
    public SemesterSummaryResponse summarizeSemester(String email, Long id) {
        User user = findUser(email);
        Semester semester = findOwnedSemester(id, user.getId());
        List<AcademicSubject> subjects = subjectRepository
                .findAllBySemester_IdAndSemester_AcademicYear_User_IdOrderByIdAsc(id, user.getId());
        return summarizeSemester(semester, subjects);
    }

    @Transactional(readOnly = true)
    public AcademicYearSummaryResponse summarizeYear(String email, Long id) {
        User user = findUser(email);
        AcademicYear year = findOwnedYear(id, user.getId());
        List<Semester> semesters = semesterRepository
                .findAllByAcademicYear_IdAndAcademicYear_User_IdOrderByStartDateAscIdAsc(id, user.getId());
        List<AcademicSubject> subjects = subjectRepository
                .findAllBySemester_AcademicYear_IdAndSemester_AcademicYear_User_IdOrderByIdAsc(id, user.getId());
        Map<Long, List<AcademicSubject>> bySemester = subjects.stream()
                .collect(Collectors.groupingBy(subject -> subject.getSemester().getId()));
        List<SemesterSummaryResponse> summaries = semesters.stream()
                .map(semester -> summarizeSemester(
                        semester, bySemester.getOrDefault(semester.getId(), List.of())))
                .toList();
        return new AcademicYearSummaryResponse(
                AcademicYearResponse.from(year),
                subjects.size(),
                eligibleSubjects(subjects).size(),
                eligibleCredits(subjects),
                weightedGradePoints(subjects),
                calculateGpa(subjects),
                summaries);
    }

    @Transactional(readOnly = true)
    public AcademicSummaryResponse summarizeAcademicHistory(String email) {
        User user = findUser(email);
        List<AcademicYear> years = yearRepository.findAllByUser_IdOrderByStartDateDescIdDesc(user.getId());
        List<AcademicYearSummaryResponse> summaries = years.stream()
                .map(year -> summarizeYear(email, year.getId()))
                .toList();
        List<AcademicSubject> subjects = subjectRepository
                .findAllBySemester_AcademicYear_User_IdOrderByIdAsc(user.getId());
        return new AcademicSummaryResponse(
                subjects.size(),
                eligibleSubjects(subjects).size(),
                eligibleCredits(subjects),
                weightedGradePoints(subjects),
                calculateGpa(subjects),
                summaries);
    }

    private SemesterSummaryResponse summarizeSemester(Semester semester, List<AcademicSubject> subjects) {
        return new SemesterSummaryResponse(
                SemesterResponse.from(semester),
                subjects.size(),
                eligibleSubjects(subjects).size(),
                eligibleCredits(subjects),
                weightedGradePoints(subjects),
                calculateGpa(subjects),
                subjects.stream().map(AcademicSubjectResponse::from).toList());
    }

    private List<AcademicSubject> eligibleSubjects(List<AcademicSubject> subjects) {
        return subjects.stream()
                .filter(subject -> subject.getOfficialGrade() != null
                        && subject.getOfficialGrade() != OfficialGrade.U)
                .toList();
    }

    private int eligibleCredits(List<AcademicSubject> subjects) {
        return eligibleSubjects(subjects).stream().mapToInt(AcademicSubject::getCredits).sum();
    }

    private BigDecimal weightedGradePoints(List<AcademicSubject> subjects) {
        return eligibleSubjects(subjects).stream()
                .map(subject -> BigDecimal.valueOf(subject.getCredits())
                        .multiply(BigDecimal.valueOf(subject.getOfficialGrade().getGradePoint())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateGpa(List<AcademicSubject> subjects) {
        int credits = eligibleCredits(subjects);
        if (credits == 0) {
            return null;
        }
        return weightedGradePoints(subjects)
                .divide(BigDecimal.valueOf(credits), 2, RoundingMode.HALF_UP);
    }

    private void ensureCodeAvailable(Long semesterId, String code, Long exceptId) {
        if (code == null) {
            return;
        }
        boolean exists = exceptId == null
                ? subjectRepository.existsBySemester_IdAndCodeIgnoreCase(semesterId, code)
                : subjectRepository.existsBySemester_IdAndCodeIgnoreCaseAndIdNot(semesterId, code, exceptId);
        if (exists) {
            throw new AcademicConflictException("Subject code must be unique within its semester.");
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private User findUser(String email) {
        return userRepository.findByNormalizedEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user was not found"));
    }

    private AcademicYear findOwnedYear(Long id, Long userId) {
        return yearRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(AcademicResourceNotFoundException::new);
    }

    private Semester findOwnedSemester(Long id, Long userId) {
        return semesterRepository.findByIdAndAcademicYear_User_Id(id, userId)
                .orElseThrow(AcademicResourceNotFoundException::new);
    }

    private AcademicSubject findOwnedSubject(Long id, Long userId) {
        return subjectRepository.findByIdAndSemester_AcademicYear_User_Id(id, userId)
                .orElseThrow(AcademicResourceNotFoundException::new);
    }
}
