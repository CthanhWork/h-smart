package com.hsmart.backend.service.impl;

import com.hsmart.backend.domain.entities.AccountToken;
import com.hsmart.backend.domain.entities.AccountTokenType;
import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.config.AccountLifecycleProperties;
import com.hsmart.backend.infrastructure.exception.DuplicateResourceException;
import com.hsmart.backend.infrastructure.exception.InvalidAccountTokenException;
import com.hsmart.backend.infrastructure.exception.InvalidCredentialsException;
import com.hsmart.backend.infrastructure.exception.InvalidRequestException;
import com.hsmart.backend.infrastructure.exception.ResourceNotFoundException;
import com.hsmart.backend.infrastructure.persistence.AccountTokenRepository;
import com.hsmart.backend.infrastructure.persistence.UserRepository;
import com.hsmart.backend.service.AccountEmailService;
import com.hsmart.backend.service.AccountLifecycleService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccountLifecycleServiceImpl implements AccountLifecycleService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;
    private static final int OTP_BOUND = 1_000_000;
    private static final int TOKEN_SAVE_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final AccountTokenRepository tokenRepository;
    private final AccountEmailService accountEmailService;
    private final AccountLifecycleProperties properties;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void sendVerificationEmail(String email) {
        userRepository.findByEmail(normalizeEmail(email)).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                String token = issueToken(
                        user,
                        AccountTokenType.EMAIL_VERIFICATION,
                        Duration.ofMinutes(properties.verificationTokenMinutes())
                );
                accountEmailService.sendVerificationEmail(user, token);
            }
        });
    }

    @Override
    public void verifyEmail(String rawToken) {
        AccountToken token = requireUsableToken(rawToken, AccountTokenType.EMAIL_VERIFICATION);
        User user = token.getUser();
        user.setEmailVerified(true);
        token.setUsedAt(Instant.now());
        userRepository.save(user);
        tokenRepository.save(token);
        log.info("Verified email address for user {}", user.getUsername());
    }

    @Override
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(normalizeEmail(email)).ifPresent(user -> {
            if (!user.isActive()) {
                return;
            }
            String token = issueToken(
                    user,
                    AccountTokenType.PASSWORD_RESET,
                    Duration.ofMinutes(properties.passwordResetTokenMinutes())
            );
            accountEmailService.sendPasswordResetEmail(user, token);
        });
    }

    @Override
    public void resetPassword(String rawToken, String newPassword) {
        AccountToken token = requireUsableToken(rawToken, AccountTokenType.PASSWORD_RESET);
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        token.setUsedAt(Instant.now());
        tokenRepository.deleteAllByUserAndType(user, AccountTokenType.PASSWORD_RESET);
        revokeRefreshTokens(user);
        userRepository.save(user);
        log.info("Reset password for user {}", user.getUsername());
    }

    @Override
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = findUserByUsername(username);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new InvalidRequestException("New password must be different from the current password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        revokeRefreshTokens(user);
        userRepository.save(user);
        log.info("Changed password for user {}", user.getUsername());
    }

    @Override
    public void requestEmailChange(String username, String newEmail) {
        User user = findUserByUsername(username);
        String normalizedEmail = normalizeEmail(newEmail);

        if (normalizedEmail.equals(user.getEmail())) {
            throw new InvalidRequestException("New email must be different from the current email");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Email already exists");
        }

        String token = issueToken(
                user,
                AccountTokenType.EMAIL_CHANGE,
                Duration.ofMinutes(properties.emailChangeTokenMinutes()),
                normalizedEmail
        );
        accountEmailService.sendEmailChangeEmail(user, normalizedEmail, token);
        log.info("Issued email change token for user {}", user.getUsername());
    }

    @Override
    public void confirmEmailChange(String username, String rawToken) {
        User user = findUserByUsername(username);
        AccountToken token = requireUsableToken(rawToken, AccountTokenType.EMAIL_CHANGE);
        if (!token.getUser().getId().equals(user.getId()) || !StringUtils.hasText(token.getTargetEmail())) {
            throw new InvalidAccountTokenException();
        }

        String normalizedEmail = normalizeEmail(token.getTargetEmail());
        if (!normalizedEmail.equals(user.getEmail()) && userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Email already exists");
        }

        user.setEmail(normalizedEmail);
        user.setEmailVerified(true);
        tokenRepository.deleteAllByUserAndType(user, AccountTokenType.EMAIL_CHANGE);
        userRepository.save(user);
        log.info("Changed email for user {}", user.getUsername());
    }

    @Override
    public void revokeRefreshTokens(User user) {
        tokenRepository.deleteAllByUserAndType(user, AccountTokenType.REFRESH_TOKEN);
    }

    private String issueToken(User user, AccountTokenType type, Duration lifetime) {
        return issueToken(user, type, lifetime, null);
    }

    private String issueToken(User user, AccountTokenType type, Duration lifetime, String targetEmail) {
        tokenRepository.deleteAllByUserAndType(user, type);
        Instant now = Instant.now();

        for (int attempt = 0; attempt < TOKEN_SAVE_ATTEMPTS; attempt++) {
            String rawToken = generateOtpCode();
            try {
                tokenRepository.save(AccountToken.builder()
                        .user(user)
                        .type(type)
                        .tokenHash(hash(rawToken))
                        .targetEmail(targetEmail)
                        .createdAt(now)
                        .expiresAt(now.plus(lifetime))
                        .build());
                return rawToken;
            } catch (DataIntegrityViolationException exception) {
                log.warn("Generated duplicate OTP for user {} and token type {}. Retrying.", user.getUsername(), type);
            }
        }

        throw new IllegalStateException("Failed to issue OTP token after repeated attempts");
    }

    private AccountToken requireUsableToken(String rawToken, AccountTokenType type) {
        AccountToken token = tokenRepository.findByTokenHashAndType(hash(rawToken), type)
                .orElseThrow(InvalidAccountTokenException::new);
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidAccountTokenException();
        }
        return token;
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String generateOtpCode() {
        return String.format("%0" + OTP_LENGTH + "d", SECURE_RANDOM.nextInt(OTP_BOUND));
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizeToken(rawToken).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String normalizeToken(String rawToken) {
        if (rawToken == null) {
            return "";
        }
        return rawToken.replaceAll("\\s+", "").trim();
    }
}
