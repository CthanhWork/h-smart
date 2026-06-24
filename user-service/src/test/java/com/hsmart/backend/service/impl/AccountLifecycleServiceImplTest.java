package com.hsmart.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hsmart.backend.domain.entities.AccountToken;
import com.hsmart.backend.domain.entities.AccountTokenType;
import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.config.AccountLifecycleProperties;
import com.hsmart.backend.infrastructure.exception.InvalidAccountTokenException;
import com.hsmart.backend.infrastructure.persistence.AccountTokenRepository;
import com.hsmart.backend.infrastructure.persistence.UserRepository;
import com.hsmart.backend.service.AccountEmailService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountLifecycleServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountTokenRepository tokenRepository;
    @Mock
    private AccountEmailService accountEmailService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountLifecycleServiceImpl(
                userRepository,
                tokenRepository,
                accountEmailService,
                new AccountLifecycleProperties("https://hsmart.example", 1440, 30, 30, 10080),
                passwordEncoder
        );
    }

    @Test
    void passwordResetRequestShouldStoreOnlyHashedSixDigitOtp() {
        User user = activeUser();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        service.requestPasswordReset(" USER@example.com ");

        ArgumentCaptor<AccountToken> storedToken = ArgumentCaptor.forClass(AccountToken.class);
        ArgumentCaptor<String> rawToken = ArgumentCaptor.forClass(String.class);
        verify(tokenRepository).save(storedToken.capture());
        verify(accountEmailService).sendPasswordResetEmail(eq(user), rawToken.capture());

        assertTrue(rawToken.getValue().matches("\\d{6}"));
        assertNotEquals(rawToken.getValue(), storedToken.getValue().getTokenHash());
    }

    @Test
    void verifyEmailShouldActivateEmailAndConsumeToken() {
        User user = activeUser();
        user.setEmailVerified(false);
        AccountToken token = usableToken(user, AccountTokenType.EMAIL_VERIFICATION);
        when(tokenRepository.findByTokenHashAndType(anyString(), eq(AccountTokenType.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(token));

        service.verifyEmail("raw-token");

        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
        assertTrue(user.isEmailVerified());
        assertTrue(token.getUsedAt() != null);
    }

    @Test
    void expiredPasswordResetTokenShouldBeRejected() {
        User user = activeUser();
        AccountToken token = usableToken(user, AccountTokenType.PASSWORD_RESET);
        token.setExpiresAt(Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHashAndType(anyString(), eq(AccountTokenType.PASSWORD_RESET)))
                .thenReturn(Optional.of(token));

        assertThrows(
                InvalidAccountTokenException.class,
                () -> service.resetPassword("expired-token", "NewPassword123!")
        );
    }

    @Test
    void passwordResetShouldEncodePasswordAndInvalidateResetTokens() {
        User user = activeUser();
        AccountToken token = usableToken(user, AccountTokenType.PASSWORD_RESET);
        when(tokenRepository.findByTokenHashAndType(anyString(), eq(AccountTokenType.PASSWORD_RESET)))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("encoded-password");

        service.resetPassword("raw-token", "NewPassword123!");

        verify(tokenRepository).deleteAllByUserAndType(user, AccountTokenType.PASSWORD_RESET);
        verify(userRepository).save(user);
        org.junit.jupiter.api.Assertions.assertEquals("encoded-password", user.getPassword());
    }

    private User activeUser() {
        return User.builder()
                .id(10L)
                .username("user")
                .email("user@example.com")
                .password("old-password")
                .active(true)
                .emailVerified(true)
                .build();
    }

    private AccountToken usableToken(User user, AccountTokenType type) {
        return AccountToken.builder()
                .id(20L)
                .user(user)
                .type(type)
                .tokenHash("hash")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
    }
}
