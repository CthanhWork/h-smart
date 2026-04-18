package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.CategoryRequestDTO;
import com.hsmart.backend.application.dto.CategoryResponseDTO;
import com.hsmart.backend.application.exceptions.DuplicateCategoryException;
import com.hsmart.backend.application.mapper.CategoryMapper;
import com.hsmart.backend.domain.entities.Category;
import com.hsmart.backend.infrastructure.persistence.CategoryRepository;
import com.hsmart.backend.service.CategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public CategoryResponseDTO createCategory(CategoryRequestDTO request) {
        String normalizedName = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new DuplicateCategoryException(normalizedName);
        }

        Category category = categoryMapper.toEntity(CategoryRequestDTO.builder().name(normalizedName).build());
        Category savedCategory = categoryRepository.save(category);
        log.info("Created category with id {} and name {}", savedCategory.getId(), savedCategory.getName());
        return categoryMapper.toResponse(savedCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> getAllCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }
}
