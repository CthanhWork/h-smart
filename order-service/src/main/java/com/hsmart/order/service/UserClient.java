package com.hsmart.order.service;

import com.hsmart.order.application.dto.UserAddressResponseDTO;

public interface UserClient {
    UserAddressResponseDTO getUserAddress(String userId);
}
