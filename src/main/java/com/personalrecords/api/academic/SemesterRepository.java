package com.personalrecords.api.academic;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    List<Semester> findAllByAcademicYear_IdAndAcademicYear_User_IdOrderByStartDateAscIdAsc(
            Long academicYearId, Long userId);
    Optional<Semester> findByIdAndAcademicYear_User_Id(Long id, Long userId);
    boolean existsByAcademicYear_IdAndAcademicYear_User_Id(Long academicYearId, Long userId);
}
