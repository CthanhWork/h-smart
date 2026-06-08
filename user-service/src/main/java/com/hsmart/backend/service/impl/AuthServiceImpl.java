package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.AuthResponseDTO;
import com.hsmart.backend.application.dto.LoginRequestDTO;
import com.hsmart.backend.application.dto.RegisterRequestDTO;
import com.hsmart.backend.application.mapper.UserMapper;
import com.hsmart.backend.domain.entities.Role;
import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.config.JwtService;
import com.hsmart.backend.infrastructure.exception.DuplicateResourceException;
import com.hsmart.backend.infrastructure.exception.AccountBannedException;
import com.hsmart.backend.infrastructure.exception.AccountNotVerifiedException;
import com.hsmart.backend.infrastructure.exception.InvalidCredentialsException;
import com.hsmart.backend.infrastructure.persistence.UserRepository;
import com.hsmart.backend.service.AuthService;
import com.hsmart.backend.service.AccountLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AccountLifecycleService accountLifecycleService;

    @Override
    public AuthResponseDTO register(RegisterRequestDTO request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

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
                .province(request.getProvince())
                .district(request.getDistrict())
                .ward(request.getWard())
                .streetDetail(request.getStreetDetail())
                .avatarUrl(request.getAvatarUrl())
                .build());

        accountLifecycleService.sendVerificationEmail(savedUser.getEmail());
        return userMapper.toAuthResponse(savedUser, null);
    }

    @Override
    @Transactional(readOnly = true)
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

        return userMapper.toAuthResponse(user, jwtService.generateToken(user));
    }
}
