package com.hsmart.backend.infrastructure.context;

import java.util.Optional;

public final class UserContextHolder {

    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void setCurrentUserId(String userId) {
        CURRENT_USER.set(userId);
    }

    public static Optional<String> getCurrentUserId() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
