package com.hsmart.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hsmart.backend.application.dto.AuthResponseDTO;
import com.hsmart.backend.application.dto.LoginRequestDTO;
import com.hsmart.backend.application.dto.RegisterRequestDTO;
import com.hsmart.backend.application.mapper.UserMapper;
import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.config.AccountLifecycleProperties;
import com.hsmart.backend.infrastructure.config.ApplicationProperties;
import com.hsmart.backend.infrastructure.config.JwtService;
import com.hsmart.backend.infrastructure.exception.AccountNotVerifiedException;
import com.hsmart.backend.infrastructure.exception.InvalidCredentialsException;
import com.hsmart.backend.infrastructure.persistence.AccountTokenRepository;
import com.hsmart.backend.infrastructure.persistence.UserRepository;
import com.hsmart.backend.service.AccountLifecycleService;
import com.hsmart.backend.service.LocationCatalogService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AccountTokenRepository accountTokenRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AccountLifecycleService accountLifecycleService;
    @Mock
    private LocationCatalogService locationCatalogService;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                userRepository,
                accountTokenRepository,
                passwordEncoder,
                jwtService,
                userMapper,
                accountLifecycleService,
                locationCatalogService,
                new AccountLifecycleProperties("https://hsmart.example", 1440, 30, 30, 10080),
                new ApplicationProperties("http://localhost:8000")
        );
    }

    @Test
    void registerShouldCreateUnverifiedUserWithoutJwtAndSendVerificationEmail() {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("new-user")
                .email("USER@example.com")
                .password("Password123!")
                .build();
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toAuthResponse(any(User.class), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(AuthResponseDTO.builder().build());

        service.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertFalse(savedUser.isEmailVerified());
        verify(accountLifecycleService).sendVerificationEmail("user@example.com");
        verifyNoInteractions(jwtService);
    }

    @Test
    void loginShouldHideVerificationStateWhenPasswordIsIncorrect() {
        User user = User.builder()
                .username("user")
                .email("user@example.com")
                .password("encoded")
                .active(true)
                .emailVerified(false)
                .build();
        when(userRepository.findByUsernameOrEmail("user", "user")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded")).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(LoginRequestDTO.builder()
                        .usernameOrEmail("user")
                        .password("wrong-password")
                        .build())
        );
        verifyNoInteractions(jwtService);
    }

    @Test
    void loginShouldRejectCorrectCredentialsUntilEmailIsVerified() {
        User user = User.builder()
                .username("user")
                .email("user@example.com")
                .password("encoded")
                .active(true)
                .emailVerified(false)
                .build();
        when(userRepository.findByUsernameOrEmail("user", "user")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "encoded")).thenReturn(true);

        assertThrows(
                AccountNotVerifiedException.class,
                () -> service.login(LoginRequestDTO.builder()
                        .usernameOrEmail("user")
                        .password("Password123!")
                        .build())
        );
        verifyNoInteractions(jwtService);
    }
}
