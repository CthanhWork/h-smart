package com.hsmart.backend.service.impl;

import com.hsmart.backend.domain.entities.AccountToken;
import com.hsmart.backend.domain.entities.AccountTokenType;
import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.config.AccountLifecycleProperties;
import com.hsmart.backend.infrastructure.exception.InvalidAccountTokenException;
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
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccountLifecycleServiceImpl implements AccountLifecycleService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
        userRepository.save(user);
        log.info("Reset password for user {}", user.getUsername());
    }

    private String issueToken(User user, AccountTokenType type, Duration lifetime) {
        tokenRepository.deleteAllByUserAndType(user, type);
        String rawToken = generateRawToken();
        Instant now = Instant.now();
        tokenRepository.save(AccountToken.builder()
                .user(user)
                .type(type)
                .tokenHash(hash(rawToken))
                .createdAt(now)
                .expiresAt(now.plus(lifetime))
                .build());
        return rawToken;
    }

    private AccountToken requireUsableToken(String rawToken, AccountTokenType type) {
        AccountToken token = tokenRepository.findByTokenHashAndType(hash(rawToken), type)
                .orElseThrow(InvalidAccountTokenException::new);
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidAccountTokenException();
        }
        return token;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
