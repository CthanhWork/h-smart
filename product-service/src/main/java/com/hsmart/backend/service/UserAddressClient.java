package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.UserAddressResponseDTO;
import java.util.Optional;

public interface UserAddressClient {
    Optional<UserAddressResponseDTO> getUserAddress(String userId);
}
