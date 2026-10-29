package com.personalrecords.api.academic;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicSubjectRepository extends JpaRepository<AcademicSubject, Long> {
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
