package com.hsmart.backend.service;

import com.hsmart.backend.domain.entities.User;

public interface AccountLifecycleService {
    void sendVerificationEmail(String email);
    void verifyEmail(String rawToken);
    void requestPasswordReset(String email);
    void resetPassword(String rawToken, String newPassword);
    void changePassword(String username, String currentPassword, String newPassword);
    void requestEmailChange(String username, String newEmail);
    void confirmEmailChange(String username, String rawToken);
    void revokeRefreshTokens(User user);
}
