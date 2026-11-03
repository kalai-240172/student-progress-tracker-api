package com.personalrecords.api.user;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String normalizedEmail);

    default Optional<User> findByNormalizedEmail(String email) {
        return findByEmail(User.normalizeEmail(email));
    }

    default boolean existsByNormalizedEmail(String email) {
        return existsByEmail(User.normalizeEmail(email));
    }
}