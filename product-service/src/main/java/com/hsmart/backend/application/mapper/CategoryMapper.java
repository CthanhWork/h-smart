package com.hsmart.backend.application.mapper;

import com.hsmart.backend.application.dto.CategoryRequestDTO;
import com.hsmart.backend.application.dto.CategoryResponseDTO;
import com.hsmart.backend.domain.entities.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponseDTO toResponse(Category category);

    @Mapping(target = "id", ignore = true)
    Category toEntity(CategoryRequestDTO request);
}
