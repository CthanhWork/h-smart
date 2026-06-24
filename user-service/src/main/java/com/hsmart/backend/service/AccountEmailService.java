package com.hsmart.backend.service;

import com.hsmart.backend.domain.entities.User;

public interface AccountEmailService {
    void sendVerificationEmail(User user, String rawToken);
    void sendPasswordResetEmail(User user, String rawToken);
    void sendEmailChangeEmail(User user, String newEmail, String rawToken);
}
