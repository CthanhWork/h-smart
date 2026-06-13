package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.ProductListingSuggestionResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ProductListingSuggestionService {
    ProductListingSuggestionResponseDTO prepareListing(MultipartFile file);
}
