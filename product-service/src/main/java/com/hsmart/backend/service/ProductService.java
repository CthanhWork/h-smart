package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.dto.ProductStatsResponseDTO;
import com.hsmart.backend.application.dto.ProductRequestDTO;
import com.hsmart.backend.application.dto.ProductResponseDTO;
import com.hsmart.backend.domain.entities.ProductStatus;
import java.io.IOException;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProductService {
    ProductResponseDTO createProduct(ProductRequestDTO request, MultipartFile image) throws IOException;
    ProductResponseDTO updateProduct(Long id, ProductRequestDTO request);
    void deleteProduct(Long id);
    PageResponseDTO<ProductResponseDTO> getAllProducts(String keyword, ProductStatus status, Long categoryId, Pageable pageable);
    PageResponseDTO<ProductResponseDTO> getWishlist(Pageable pageable);
    ProductResponseDTO getProductById(Long id);
    String toggleProductLike(Long id);
    void markProductSoldFromOrderEvent(Long productId);
    ProductResponseDTO updateModerationStatus(Long id, ProductStatus status);
    ProductStatsResponseDTO getProductStats();
}
