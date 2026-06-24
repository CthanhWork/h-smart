package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.AuthResponseDTO;
import com.hsmart.backend.application.dto.LoginRequestDTO;
import com.hsmart.backend.application.dto.RefreshTokenRequestDTO;
import com.hsmart.backend.application.dto.RegisterRequestDTO;
import com.hsmart.backend.application.dto.ResolvedLocationDTO;
import com.hsmart.backend.application.mapper.UserMapper;
import com.hsmart.backend.domain.entities.AccountToken;
import com.hsmart.backend.domain.entities.AccountTokenType;
import com.hsmart.backend.domain.entities.Role;
import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.config.AccountLifecycleProperties;
import com.hsmart.backend.infrastructure.config.ApplicationProperties;
import com.hsmart.backend.infrastructure.config.JwtService;
import com.hsmart.backend.infrastructure.exception.DuplicateResourceException;
import com.hsmart.backend.infrastructure.exception.AccountBannedException;
import com.hsmart.backend.infrastructure.exception.AccountNotVerifiedException;
import com.hsmart.backend.infrastructure.exception.InvalidLocationException;
import com.hsmart.backend.infrastructure.exception.InvalidCredentialsException;
import com.hsmart.backend.infrastructure.exception.InvalidAccountTokenException;
import com.hsmart.backend.infrastructure.persistence.AccountTokenRepository;
import com.hsmart.backend.infrastructure.persistence.UserRepository;
import com.hsmart.backend.service.AuthService;
import com.hsmart.backend.service.AccountLifecycleService;
import com.hsmart.backend.service.LocationCatalogService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AccountLifecycleService accountLifecycleService;
    private final LocationCatalogService locationCatalogService;
    private final AccountLifecycleProperties accountLifecycleProperties;
    private final ApplicationProperties applicationProperties;

    @Override
    public AuthResponseDTO register(RegisterRequestDTO request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();
        ResolvedLocationDTO resolvedLocation = resolveLocationIfPresent(request);

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already exists");
        }

        User savedUser = userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .emailVerified(false)
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .provinceCode(resolvedLocation == null ? null : resolvedLocation.provinceCode())
                .province(resolvedLocation == null ? null : resolvedLocation.provinceName())
                .districtCode(resolvedLocation == null ? null : resolvedLocation.districtCode())
                .district(resolvedLocation == null ? null : resolvedLocation.districtName())
                .wardCode(resolvedLocation == null ? null : resolvedLocation.wardCode())
                .ward(resolvedLocation == null ? null : resolvedLocation.wardName())
                .streetDetail(normalizeOptionalText(request.getStreetDetail()))
                .avatarUrl(request.getAvatarUrl())
                .build());

        accountLifecycleService.sendVerificationEmail(savedUser.getEmail());
        return toAuthResponse(savedUser, null, null);
    }

    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {
        String identifier = request.getUsernameOrEmail().trim();
        User user = userRepository.findByUsernameOrEmail(identifier, identifier.toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username/email or password"));

        if (!user.isActive()) {
            throw new AccountBannedException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username/email or password");
        }

        if (!user.isEmailVerified()) {
            throw new AccountNotVerifiedException();
        }

        return toAuthResponse(user, jwtService.generateToken(user), issueRefreshToken(user));
    }

    @Override
    public AuthResponseDTO refresh(RefreshTokenRequestDTO request) {
        AccountToken refreshToken = requireUsableRefreshToken(request.refreshToken());
        User user = refreshToken.getUser();

        if (!user.isActive()) {
            throw new AccountBannedException();
        }
        if (!user.isEmailVerified()) {
            throw new AccountNotVerifiedException();
        }

        accountTokenRepository.deleteAllByUserAndType(user, AccountTokenType.REFRESH_TOKEN);
        return toAuthResponse(user, jwtService.generateToken(user), issueRefreshToken(user));
    }

    @Override
    public void logout(RefreshTokenRequestDTO request) {
        if (!StringUtils.hasText(request.refreshToken())) {
            return;
        }

        accountTokenRepository.findByTokenHashAndType(hash(request.refreshToken()), AccountTokenType.REFRESH_TOKEN)
                .ifPresent(token -> accountTokenRepository.deleteAllByUserAndType(token.getUser(), AccountTokenType.REFRESH_TOKEN));
    }

    private ResolvedLocationDTO resolveLocationIfPresent(RegisterRequestDTO request) {
        boolean hasAnyAddressInput = StringUtils.hasText(request.getProvinceCode())
                || StringUtils.hasText(request.getDistrictCode())
                || StringUtils.hasText(request.getWardCode())
                || StringUtils.hasText(request.getStreetDetail());
        if (!hasAnyAddressInput) {
            return null;
        }

        if (!StringUtils.hasText(request.getStreetDetail())) {
            throw new InvalidLocationException("Street detail is required when an address is provided");
        }

        return locationCatalogService.resolveLocation(
                request.getProvinceCode(),
                request.getDistrictCode(),
                request.getWardCode()
        );
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String issueRefreshToken(User user) {
        accountLifecycleService.revokeRefreshTokens(user);
        String rawToken = generateRefreshToken();
        Instant now = Instant.now();
        accountTokenRepository.save(AccountToken.builder()
                .user(user)
                .type(AccountTokenType.REFRESH_TOKEN)
                .tokenHash(hash(rawToken))
                .createdAt(now)
                .expiresAt(now.plusSeconds(accountLifecycleProperties.refreshTokenMinutes() * 60))
                .build());
        return rawToken;
    }

    private AccountToken requireUsableRefreshToken(String rawToken) {
        AccountToken token = accountTokenRepository.findByTokenHashAndType(hash(rawToken), AccountTokenType.REFRESH_TOKEN)
                .orElseThrow(InvalidAccountTokenException::new);
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidAccountTokenException();
        }
        return token;
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private String normalizeToken(String rawToken) {
        if (rawToken == null) {
            return "";
        }
        return rawToken.trim();
    }

    private AuthResponseDTO toAuthResponse(User user, String accessToken, String refreshToken) {
        AuthResponseDTO response = userMapper.toAuthResponse(user, accessToken, refreshToken);
        if (response.getUser() != null) {
            response.getUser().setAvatarUrl(toAbsoluteAvatarUrl(response.getUser().getAvatarUrl()));
        }
        return response;
    }

    private String toAbsoluteAvatarUrl(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return avatarUrl;
        }

        String normalizedAvatarUrl = avatarUrl.trim();
        if (normalizedAvatarUrl.startsWith("http://") || normalizedAvatarUrl.startsWith("https://")) {
            return normalizedAvatarUrl;
        }

        String publicBaseUrl = normalizeBaseUrl(applicationProperties.publicBaseUrl());
        if (publicBaseUrl.isEmpty()) {
            return normalizedAvatarUrl;
        }

        String normalizedPath = normalizedAvatarUrl.startsWith("/") ? normalizedAvatarUrl : "/" + normalizedAvatarUrl;
        return publicBaseUrl + normalizedPath;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
