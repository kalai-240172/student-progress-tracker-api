package com.personalrecords.api.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    default Optional<User> findByNormalizedEmail(String email) {
        return findByEmail(User.normalizeEmail(email));
    }

    default boolean existsByNormalizedEmail(String email) {
        return existsByEmail(User.normalizeEmail(email));
    }
}