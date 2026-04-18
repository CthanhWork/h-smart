package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.ProductDTO;
import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.dto.ProductResponseDTO;
import java.io.IOException;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProductService {
    ProductResponseDTO createProduct(ProductDTO productDto, MultipartFile image) throws IOException;
    PageResponseDTO<ProductResponseDTO> getAllProducts(Pageable pageable);
    ProductResponseDTO getProductById(Long id);
    ProductResponseDTO updateProduct(Long id, ProductDTO productDto, MultipartFile image) throws IOException;
    void deleteProduct(Long id);
}
