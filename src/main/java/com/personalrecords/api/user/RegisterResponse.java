package com.personalrecords.api.user;

import java.time.LocalDateTime;

public record RegisterResponse(Long id, String name, String email, LocalDateTime createdAt) {

    public static RegisterResponse from(User user) {
        return new RegisterResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
