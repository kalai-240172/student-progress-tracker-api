package com.personalrecords.api.academic;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicSubjectRepository extends JpaRepository<AcademicSubject, Long> {
    Stream<AcademicSubject> streamAllBySemester_IdAndSemester_AcademicYear_User_IdOrderByIdAsc(
            Long semesterId, Long userId);
    Stream<AcademicSubject> streamAllBySemester_AcademicYear_IdAndSemester_AcademicYear_User_IdOrderByIdAsc(
            Long academicYearId, Long userId);
    Stream<AcademicSubject> streamAllBySemester_AcademicYear_User_IdOrderByIdAsc(Long userId);
    List<AcademicSubject> findAllBySemester_IdAndSemester_AcademicYear_User_IdOrderByIdAsc(
            Long semesterId, Long userId);
    List<AcademicSubject> findAllBySemester_AcademicYear_IdAndSemester_AcademicYear_User_IdOrderByIdAsc(
            Long academicYearId, Long userId);
    List<AcademicSubject> findAllBySemester_AcademicYear_User_IdOrderByIdAsc(Long userId);
    Optional<AcademicSubject> findByIdAndSemester_AcademicYear_User_Id(Long id, Long userId);
    boolean existsBySemester_IdAndCodeIgnoreCase(Long semesterId, String code);
    boolean existsBySemester_IdAndCodeIgnoreCaseAndIdNot(Long semesterId, String code, Long id);
    boolean existsBySemester_Id(Long semesterId);
}
