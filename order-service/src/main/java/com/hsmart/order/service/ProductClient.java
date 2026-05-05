package com.hsmart.order.service;

import com.hsmart.order.application.dto.ProductResponseDTO;

public interface ProductClient {
    ProductResponseDTO getProduct(Long productId, String buyerId);
}
