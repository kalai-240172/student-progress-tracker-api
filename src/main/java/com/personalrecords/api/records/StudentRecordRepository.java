package com.personalrecords.api.records;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRecordRepository extends JpaRepository<StudentRecord, Long> {

    List<StudentRecord> findAllByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<StudentRecord> findByIdAndUser_Id(Long id, Long userId);
}
