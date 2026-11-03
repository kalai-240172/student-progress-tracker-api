package com.personalrecords.api.sample;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleDataInitializationRepository extends JpaRepository<SampleDataInitialization, Long> {

    boolean existsByUser_Id(Long userId);
}
