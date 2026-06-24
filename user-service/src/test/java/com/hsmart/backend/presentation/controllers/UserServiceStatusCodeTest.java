package com.hsmart.backend.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.AuthResponseDTO;
import com.hsmart.backend.application.dto.ChangeEmailRequestDTO;
import com.hsmart.backend.application.dto.ChangePasswordRequestDTO;
import com.hsmart.backend.application.dto.LoginRequestDTO;
import com.hsmart.backend.application.dto.LocationOptionDTO;
import com.hsmart.backend.application.dto.RefreshTokenRequestDTO;
import com.hsmart.backend.application.dto.RegisterRequestDTO;
import com.hsmart.backend.application.dto.SellerTrustResponseDTO;
import com.hsmart.backend.application.dto.UpdateProfileRequestDTO;
import com.hsmart.backend.application.dto.UserAddressResponseDTO;
import com.hsmart.backend.application.dto.UserProfileResponseDTO;
import com.hsmart.backend.domain.entities.Role;
import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.config.JwtService;
import com.hsmart.backend.infrastructure.config.AccountLifecycleProperties;
import com.hsmart.backend.infrastructure.config.ApplicationProperties;
import com.hsmart.backend.infrastructure.exception.AccountBannedException;
import com.hsmart.backend.infrastructure.exception.DuplicateResourceException;
import com.hsmart.backend.infrastructure.exception.InvalidCredentialsException;
import com.hsmart.backend.infrastructure.exception.ResourceNotFoundException;
import com.hsmart.backend.infrastructure.persistence.UserRepository;
import com.hsmart.backend.service.AuthService;
import com.hsmart.backend.service.AccountLifecycleService;
import com.hsmart.backend.service.LocationCatalogService;
import com.hsmart.backend.service.UserService;
import com.hsmart.backend.service.impl.AuthServiceImpl;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserServiceStatusCodeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    @MockBean
    private AccountLifecycleService accountLifecycleService;

    @MockBean
    private LocationCatalogService locationCatalogService;

    @Test
    void registerShouldReturn201WhenPayloadIsValid() throws Exception {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("nguyenvana")
                .email("vana@example.com")
                .password("12345678")
                .fullName("Nguyen Van A")
                .build();

        given(authService.register(any(RegisterRequestDTO.class))).willReturn(buildAuthResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value(
                        "User registered successfully. Check your email to verify the account."))
                .andExpect(jsonPath("$.data.accessToken").value("mock-token"));
    }

    @Test
    void registerShouldReturn400WhenValidationFails() throws Exception {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("abc")
                .email("invalid-email")
                .password("")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void registerShouldReturn409WhenUsernameOrEmailAlreadyExists() throws Exception {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("nguyenvana")
                .email("vana@example.com")
                .password("12345678")
                .build();

        given(authService.register(any(RegisterRequestDTO.class)))
                .willThrow(new DuplicateResourceException("Username already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void loginShouldReturn200WhenCredentialsAreValid() throws Exception {
        LoginRequestDTO request = LoginRequestDTO.builder()
                .usernameOrEmail("nguyenvana")
                .password("123456")
                .build();

        given(authService.login(any(LoginRequestDTO.class))).willReturn(buildAuthResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").value("mock-token"));
    }

    @Test
    void loginShouldReturn401WhenCredentialsAreInvalid() throws Exception {
        LoginRequestDTO request = LoginRequestDTO.builder()
                .usernameOrEmail("nguyenvana")
                .password("wrong-password")
                .build();

        given(authService.login(any(LoginRequestDTO.class)))
                .willThrow(new InvalidCredentialsException("Invalid username/email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username/email or password"));
    }

    @Test
    void loginShouldReturn403WhenAccountIsBanned() throws Exception {
        LoginRequestDTO request = LoginRequestDTO.builder()
                .usernameOrEmail("nguyenvana")
                .password("123456")
                .build();

        given(authService.login(any(LoginRequestDTO.class)))
                .willThrow(new AccountBannedException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Account has been banned"));
    }

    @Test
    void authServiceShouldRejectBannedAccountBeforeGeneratingToken() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        com.hsmart.backend.application.mapper.UserMapper userMapper =
                mock(com.hsmart.backend.application.mapper.UserMapper.class);
        AccountLifecycleService accountLifecycleService = mock(AccountLifecycleService.class);
        AuthServiceImpl service = new AuthServiceImpl(
                userRepository,
                mock(com.hsmart.backend.infrastructure.persistence.AccountTokenRepository.class),
                passwordEncoder,
                jwtService,
                userMapper,
                accountLifecycleService,
                mock(LocationCatalogService.class),
                mock(AccountLifecycleProperties.class),
                mock(ApplicationProperties.class)
        );
        User bannedUser = User.builder()
                .username("nguyenvana")
                .email("vana@example.com")
                .password("encoded-password")
                .active(false)
                .build();
        when(userRepository.findByUsernameOrEmail("nguyenvana", "nguyenvana"))
                .thenReturn(java.util.Optional.of(bannedUser));

        org.junit.jupiter.api.Assertions.assertThrows(
                AccountBannedException.class,
                () -> service.login(LoginRequestDTO.builder()
                        .usernameOrEmail("nguyenvana")
                        .password("123456")
                        .build())
        );
        verifyNoInteractions(passwordEncoder, jwtService, userMapper);
    }

    @Test
    void loginShouldReturn400WhenJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void loginShouldReturn500WhenUnexpectedErrorOccurs() throws Exception {
        LoginRequestDTO request = LoginRequestDTO.builder()
                .usernameOrEmail("nguyenvana")
                .password("123456")
                .build();

        given(authService.login(any(LoginRequestDTO.class)))
                .willThrow(new RuntimeException("Unexpected failure"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void refreshShouldReturn200WhenRefreshTokenIsValid() throws Exception {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("refresh-token");

        given(authService.refresh(any(RefreshTokenRequestDTO.class))).willReturn(buildAuthResponse());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Token refreshed successfully"))
                .andExpect(jsonPath("$.data.accessToken").value("mock-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("mock-refresh-token"));
    }

    @Test
    void logoutShouldReturn200() throws Exception {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Logout successful"));

        org.mockito.Mockito.verify(authService).logout(any(RefreshTokenRequestDTO.class));
    }

    @Test
    void getProfileShouldReturn401WhenJwtIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or missing security token"));
    }

    @Test
    void getProfileShouldReturn200WhenAuthenticatedUserExists() throws Exception {
        given(userService.getProfile("nguyenvana")).willReturn(buildUserProfile());

        mockMvc.perform(get("/api/v1/users/profile").with(user("nguyenvana")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Profile fetched successfully"))
                .andExpect(jsonPath("$.data.username").value("nguyenvana"));
    }

    @Test
    void getProfileShouldReturn404WhenAuthenticatedUserDoesNotExist() throws Exception {
        given(userService.getProfile("ghost")).willThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(get("/api/v1/users/profile").with(user("ghost")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void updateProfileShouldReturn200WhenRequestIsValid() throws Exception {
        UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                .fullName("Nguyen Van A Updated")
                .phoneNumber("0901234567")
                .provinceCode("79")
                .districtCode("760")
                .wardCode("26734")
                .streetDetail("1 Vo Van Ngan Street")
                .build();

        UserProfileResponseDTO response = buildUserProfile();
        response.setFullName("Nguyen Van A Updated");

        given(userService.updateProfile(eq("nguyenvana"), any(UpdateProfileRequestDTO.class))).willReturn(response);

        mockMvc.perform(put("/api/v1/users/profile")
                        .with(user("nguyenvana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Profile updated successfully"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A Updated"));
    }

    @Test
    void updateAvatarShouldReturn200WhenRequestIsValid() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image".getBytes()
        );

        UserProfileResponseDTO response = buildUserProfile();
        response.setAvatarUrl("http://localhost:8000/api/v1/users/media/avatar-updated.jpg");

        given(userService.updateAvatar(eq("nguyenvana"), any())).willReturn(response);

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("nguyenvana"))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Avatar updated successfully"))
                .andExpect(jsonPath("$.data.avatarUrl").value("http://localhost:8000/api/v1/users/media/avatar-updated.jpg"));
    }

    @Test
    void updateAvatarShouldAcceptAvatarPartAlias() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "avatar",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image".getBytes()
        );

        UserProfileResponseDTO response = buildUserProfile();
        response.setAvatarUrl("http://localhost:8000/api/v1/users/media/avatar-updated.jpg");

        given(userService.updateAvatar(eq("nguyenvana"), any())).willReturn(response);

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("nguyenvana"))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Avatar updated successfully"))
                .andExpect(jsonPath("$.data.avatarUrl").value("http://localhost:8000/api/v1/users/media/avatar-updated.jpg"));
    }

    @Test
    void updateAvatarShouldReturn400WhenMultipartHasNoFile() throws Exception {
        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("nguyenvana"))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Avatar image file is required"));
    }

    @Test
    void changePasswordShouldReturn200WhenRequestIsValid() throws Exception {
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO("current-password", "new-password");

        mockMvc.perform(put("/api/v1/users/password")
                        .with(user("nguyenvana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        org.mockito.Mockito.verify(accountLifecycleService)
                .changePassword("nguyenvana", "current-password", "new-password");
    }

    @Test
    void requestEmailChangeShouldReturn200WhenRequestIsValid() throws Exception {
        ChangeEmailRequestDTO request = new ChangeEmailRequestDTO("new@example.com");

        mockMvc.perform(post("/api/v1/users/email/change")
                        .with(user("nguyenvana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Email change verification has been sent to the new email address"));

        org.mockito.Mockito.verify(accountLifecycleService)
                .requestEmailChange("nguyenvana", "new@example.com");
    }

    @Test
    void confirmEmailChangeShouldReturn200WhenTokenIsValid() throws Exception {
        mockMvc.perform(post("/api/v1/users/email/confirm")
                        .with(user("nguyenvana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Email changed successfully"));

        org.mockito.Mockito.verify(accountLifecycleService)
                .confirmEmailChange("nguyenvana", "123456");
    }

    @Test
    void getProvincesShouldReturn200WhenCatalogIsAvailable() throws Exception {
        given(locationCatalogService.getProvinces()).willReturn(List.of(
                LocationOptionDTO.builder().code("79").name("Ho Chi Minh City").build()
        ));

        mockMvc.perform(get("/api/v1/locations/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Provinces fetched successfully"))
                .andExpect(jsonPath("$.data[0].code").value("79"));
    }

    @Test
    void getInternalSellerTrustShouldReturn200WhenSellerExists() throws Exception {
        given(userService.getSellerTrustProfile("seller-one")).willReturn(SellerTrustResponseDTO.builder()
                .sellerId("seller-one")
                .trustScore(BigDecimal.valueOf(4.7))
                .reviewCount(8L)
                .build());

        mockMvc.perform(get("/api/v1/users/internal/seller-one/trust"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Seller trust profile fetched successfully"))
                .andExpect(jsonPath("$.data.sellerId").value("seller-one"))
                .andExpect(jsonPath("$.data.trustScore").value(4.7))
                .andExpect(jsonPath("$.data.reviewCount").value(8));
    }

    @Test
    void getInternalUserAddressShouldReturn200WhenUserExists() throws Exception {
        given(userService.getUserAddress("seller-one")).willReturn(UserAddressResponseDTO.builder()
                .userId("seller-one")
                .fullName("Seller One")
                .phoneNumber("0901234567")
                .province("Ho Chi Minh City")
                .district("District 1")
                .ward("Ben Nghe Ward")
                .streetDetail("1 Le Loi Street")
                .build());

        mockMvc.perform(get("/api/v1/users/internal/seller-one/address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("User address fetched successfully"))
                .andExpect(jsonPath("$.data.userId").value("seller-one"))
                .andExpect(jsonPath("$.data.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.data.district").value("District 1"));
    }

    @Test
    void updateInternalUserStatusShouldReturn200() throws Exception {
        mockMvc.perform(put("/api/v1/users/internal/seller-one/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("User status updated successfully"));

        org.mockito.Mockito.verify(userService).updateUserActiveStatus("seller-one", false);
    }

    private AuthResponseDTO buildAuthResponse() {
        return AuthResponseDTO.builder()
                .accessToken("mock-token")
                .refreshToken("mock-refresh-token")
                .tokenType("Bearer")
                .user(buildUserProfile())
                .build();
    }

    private UserProfileResponseDTO buildUserProfile() {
        return UserProfileResponseDTO.builder()
                .id(1L)
                .username("nguyenvana")
                .email("vana@example.com")
                .role(Role.USER)
                .fullName("Nguyen Van A")
                .phoneNumber("0901234567")
                .provinceCode("79")
                .province("Ho Chi Minh City")
                .districtCode("760")
                .district("Thu Duc City")
                .wardCode("26734")
                .ward("Linh Trung Ward")
                .streetDetail("1 Vo Van Ngan Street")
                .avatarUrl("https://example.com/avatar.jpg")
                .build();
    }
}
