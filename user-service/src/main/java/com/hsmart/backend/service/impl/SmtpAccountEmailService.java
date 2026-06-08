package com.hsmart.backend.service.impl;

import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.config.AccountLifecycleProperties;
import com.hsmart.backend.infrastructure.config.MailProperties;
import com.hsmart.backend.infrastructure.exception.EmailDeliveryException;
import com.hsmart.backend.service.AccountEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpAccountEmailService implements AccountEmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final AccountLifecycleProperties lifecycleProperties;

    @Override
    public void sendVerificationEmail(User user, String rawToken) {
        String link = buildLink("/verify-email", rawToken);
        send(
                user.getEmail(),
                "Verify your H-Smart email address",
                "Hello " + displayName(user) + ",\n\n"
                        + "Verify your H-Smart email address using this link:\n" + link + "\n\n"
                        + "If you did not create this account, you can ignore this email."
        );
    }

    @Override
    public void sendPasswordResetEmail(User user, String rawToken) {
        String link = buildLink("/reset-password", rawToken);
        send(
                user.getEmail(),
                "Reset your H-Smart password",
                "Hello " + displayName(user) + ",\n\n"
                        + "Reset your H-Smart password using this link:\n" + link + "\n\n"
                        + "If you did not request a password reset, you can ignore this email."
        );
    }

    private void send(String recipient, String subject, String text) {
        if (!mailProperties.enabled()) {
            log.warn("Account email delivery is disabled. Email with subject '{}' was not sent to {}", subject, recipient);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.from());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
            log.info("Sent account email with subject '{}' to {}", subject, recipient);
        } catch (MailException exception) {
            log.error("Failed to send account email with subject '{}' to {}", subject, recipient, exception);
            throw new EmailDeliveryException(exception);
        }
    }

    private String buildLink(String path, String token) {
        return UriComponentsBuilder.fromUriString(lifecycleProperties.frontendBaseUrl())
                .path(path)
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername()
                : user.getFullName().trim();
    }
}
