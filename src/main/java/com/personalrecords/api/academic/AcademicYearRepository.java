package com.personalrecords.api.academic;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {
    List<AcademicYear> findAllByUser_IdOrderByStartDateDescIdDesc(Long userId);
    Optional<AcademicYear> findByIdAndUser_Id(Long id, Long userId);
    boolean existsByIdAndUser_Id(Long id, Long userId);
}
