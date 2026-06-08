package com.hsmart.backend.service;

public interface AccountLifecycleService {
    void sendVerificationEmail(String email);
    void verifyEmail(String rawToken);
    void requestPasswordReset(String email);
    void resetPassword(String rawToken, String newPassword);
}
