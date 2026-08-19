package com.hsmart.backend.application.mapper;

import com.hsmart.backend.application.dto.AuthResponseDTO;
import com.hsmart.backend.application.dto.UpdateProfileRequestDTO;
import com.hsmart.backend.application.dto.UserProfileResponseDTO;
import com.hsmart.backend.domain.entities.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserProfileResponseDTO toProfileResponse(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "trustScore", ignore = true)
    @Mapping(target = "reviewCount", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "avatarUrl", ignore = true)
    void updateProfile(UpdateProfileRequestDTO request, @MappingTarget User user);

    default AuthResponseDTO toAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(toProfileResponse(user))
                .build();
    }
}
