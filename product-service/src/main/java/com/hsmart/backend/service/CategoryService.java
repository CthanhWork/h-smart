package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.CategoryRequestDTO;
import com.hsmart.backend.application.dto.CategoryResponseDTO;
import java.util.List;

public interface CategoryService {
    CategoryResponseDTO createCategory(CategoryRequestDTO request);
    List<CategoryResponseDTO> getAllCategories();
}
