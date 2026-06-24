package com.hsmart.backend.service.impl;

import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.config.AccountLifecycleProperties;
import com.hsmart.backend.infrastructure.config.MailProperties;
import com.hsmart.backend.infrastructure.exception.EmailDeliveryException;
import com.hsmart.backend.service.AccountEmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpAccountEmailService implements AccountEmailService {

    private static final String BRAND_NAME = "H-Smart";

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final AccountLifecycleProperties lifecycleProperties;

    @Override
    public void sendVerificationEmail(User user, String rawToken) {
        String pageLink = buildPageLink("/verify-email");
        send(
                user.getEmail(),
                "Verify your H-Smart account",
                verificationPlainText(user, rawToken, pageLink),
                verificationHtml(user, rawToken, pageLink)
        );
    }

    @Override
    public void sendPasswordResetEmail(User user, String rawToken) {
        String pageLink = buildPageLink("/reset-password");
        send(
                user.getEmail(),
                "Reset your H-Smart password",
                passwordResetPlainText(user, rawToken, pageLink),
                passwordResetHtml(user, rawToken, pageLink)
        );
    }

    @Override
    public void sendEmailChangeEmail(User user, String newEmail, String rawToken) {
        String pageLink = buildPageLink("/change-email");
        send(
                newEmail,
                "Confirm your new H-Smart email",
                emailChangePlainText(user, newEmail, rawToken, pageLink),
                emailChangeHtml(user, newEmail, rawToken, pageLink)
        );
    }

    private void send(String recipient, String subject, String plainText, String htmlText) {
        if (!mailProperties.enabled()) {
            log.warn("Account email delivery is disabled. Email with subject '{}' was not sent to {}", subject, recipient);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.from(), resolvedSenderName());
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(plainText, htmlText);
            mailSender.send(message);
            log.info("Sent account email with subject '{}' to {}", subject, recipient);
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            log.error("Failed to send account email with subject '{}' to {}", subject, recipient, exception);
            throw new EmailDeliveryException(exception);
        }
    }

    private String buildPageLink(String path) {
        return UriComponentsBuilder.fromUriString(lifecycleProperties.frontendBaseUrl())
                .path(path)
                .build()
                .toUriString();
    }

    private String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername()
                : user.getFullName().trim();
    }

    private String resolvedSenderName() {
        if (mailProperties.senderName() == null || mailProperties.senderName().isBlank()) {
            return BRAND_NAME;
        }
        return mailProperties.senderName().trim();
    }

    private String verificationPlainText(User user, String otpCode, String pageLink) {
        return "Hello " + displayName(user) + ",\n\n"
                + "Thanks for creating your H-Smart account.\n"
                + "Your verification OTP is: " + otpCode + "\n"
                + "This code is valid for " + lifecycleProperties.verificationTokenMinutes() + " minutes.\n"
                + "Open: " + pageLink + "\n\n"
                + "If you did not create this account, you can ignore this email.\n\n"
                + "H-Smart";
    }

    private String passwordResetPlainText(User user, String otpCode, String pageLink) {
        return "Hello " + displayName(user) + ",\n\n"
                + "We received a request to reset your H-Smart password.\n"
                + "Your password reset OTP is: " + otpCode + "\n"
                + "This code is valid for " + lifecycleProperties.passwordResetTokenMinutes() + " minutes.\n"
                + "Open: " + pageLink + "\n\n"
                + "If you did not request this, you can ignore this email.\n\n"
                + "H-Smart";
    }

    private String emailChangePlainText(User user, String newEmail, String otpCode, String pageLink) {
        return "Hello " + displayName(user) + ",\n\n"
                + "We received a request to change your H-Smart email to " + newEmail + ".\n"
                + "Your OTP code is: " + otpCode + "\n"
                + "This code is valid for " + lifecycleProperties.emailChangeTokenMinutes() + " minutes.\n"
                + "Open: " + pageLink + "\n\n"
                + "If you did not request this change, you can ignore this email.\n\n"
                + "H-Smart";
    }

    private String verificationHtml(User user, String otpCode, String pageLink) {
        return buildEmailHtml(
                user,
                "Verify your email with OTP",
                "Enter the OTP below on the verification screen to finish creating your H-Smart account.",
                "Open verification",
                pageLink,
                otpCode,
                "Verification code",
                lifecycleProperties.verificationTokenMinutes(),
                List.of(
                        "You only need the 6-digit OTP shown below.",
                        "If you did not create this account, you can ignore this email."
                )
        );
    }

    private String passwordResetHtml(User user, String otpCode, String pageLink) {
        return buildEmailHtml(
                user,
                "Reset your password with OTP",
                "Enter the OTP below on the password reset screen, then choose a new password for your H-Smart account.",
                "Open password reset",
                pageLink,
                otpCode,
                "Password reset code",
                lifecycleProperties.passwordResetTokenMinutes(),
                List.of(
                        "This OTP works for a short time to help protect your account.",
                        "If you did not request a password reset, you can ignore this email."
                )
        );
    }

    private String emailChangeHtml(User user, String newEmail, String otpCode, String pageLink) {
        return buildEmailHtml(
                user,
                "Confirm your new email with OTP",
                "Enter the OTP below to confirm " + newEmail + " as the new email for your H-Smart account.",
                "Open email confirmation",
                pageLink,
                otpCode,
                "Email change code",
                lifecycleProperties.emailChangeTokenMinutes(),
                List.of(
                        "Only the owner of this inbox can complete the email change.",
                        "If you did not request this change, ignore this email and your current login email will stay unchanged."
                )
        );
    }

    private String buildEmailHtml(
            User user,
            String heading,
            String intro,
            String buttonLabel,
            String actionLink,
            String otpCode,
            String otpLabel,
            long validMinutes,
            List<String> notes
    ) {
        StringBuilder noteItems = new StringBuilder();
        for (String note : notes) {
            noteItems.append("<li style=\"margin:0 0 10px;\">")
                    .append(escapeHtml(note))
                    .append("</li>");
        }

        return """
                <!DOCTYPE html>
                <html lang="en">
                  <body style="margin:0;padding:0;background:#f5f1e8;font-family:Arial,sans-serif;color:#1f2937;">
                    <div style="max-width:640px;margin:0 auto;padding:32px 16px;">
                      <div style="background:#ffffff;border:1px solid #eadfce;border-radius:24px;overflow:hidden;box-shadow:0 16px 40px rgba(31,41,55,0.08);">
                        <div style="padding:20px 28px;background:linear-gradient(135deg,#123524 0%%,#2f5d50 100%%);color:#f7f4ed;">
                          <div style="font-size:13px;letter-spacing:0.18em;text-transform:uppercase;opacity:0.86;">H-Smart</div>
                          <h1 style="margin:14px 0 0;font-size:30px;line-height:1.2;">%s</h1>
                        </div>
                        <div style="padding:28px;">
                          <p style="margin:0 0 16px;font-size:16px;line-height:1.7;">Hello <strong>%s</strong>,</p>
                          <p style="margin:0 0 24px;font-size:16px;line-height:1.7;">%s</p>
                          <div style="margin:0 0 18px;padding:18px;border:1px solid #eadfce;border-radius:20px;background:#fcfaf5;">
                            <div style="font-size:12px;letter-spacing:0.14em;text-transform:uppercase;color:#6b7280;">%s</div>
                            <div style="margin-top:10px;font-size:34px;line-height:1;font-weight:800;letter-spacing:0.28em;color:#123524;font-variant-numeric:tabular-nums;">%s</div>
                            <p style="margin:14px 0 0;font-size:13px;line-height:1.7;color:#4b5563;">Code is valid for %d minutes.</p>
                          </div>
                          <div style="margin:0 0 24px;">
                            <a href="%s" style="display:inline-block;background:#123524;color:#ffffff;text-decoration:none;padding:14px 22px;border-radius:999px;font-weight:700;">
                              %s
                            </a>
                          </div>
                          <p style="margin:0 0 12px;font-size:14px;line-height:1.7;color:#4b5563;">
                            If you need to open the page manually, use this link:
                          </p>
                          <p style="margin:0 0 24px;padding:14px 16px;background:#f9f6ef;border:1px solid #eadfce;border-radius:14px;word-break:break-all;font-size:13px;line-height:1.7;color:#123524;">
                            <a href="%s" style="color:#123524;text-decoration:none;">%s</a>
                          </p>
                          <ul style="margin:0;padding-left:18px;font-size:14px;line-height:1.7;color:#4b5563;">
                            %s
                          </ul>
                        </div>
                        <div style="padding:18px 28px;border-top:1px solid #eadfce;background:#fcfaf5;font-size:12px;line-height:1.7;color:#6b7280;">
                          This email was sent by H-Smart. Please do not reply directly to this message.
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(
                escapeHtml(heading),
                escapeHtml(displayName(user)),
                escapeHtml(intro),
                escapeHtml(otpLabel),
                escapeHtml(formatOtp(otpCode)),
                validMinutes,
                escapeHtml(actionLink),
                escapeHtml(buttonLabel),
                escapeHtml(actionLink),
                escapeHtml(actionLink),
                noteItems
        );
    }

    private String formatOtp(String otpCode) {
        if (otpCode == null || otpCode.length() != 6) {
            return otpCode == null ? "" : otpCode;
        }
        return otpCode.substring(0, 3) + " " + otpCode.substring(3);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
