package com.hsmart.backend.infrastructure.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hsmart.backend.domain.entities.Category;
import com.hsmart.backend.infrastructure.persistence.CategoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

class AiCategoryInitializerTest {

    private final CategoryRepository categoryRepository = org.mockito.Mockito.mock(CategoryRepository.class);
    private final AiCategoryInitializer initializer = new AiCategoryInitializer(categoryRepository);

    @Test
    void shouldInsertMissingAiCategories() throws Exception {
        when(categoryRepository.findAll()).thenReturn(List.of(
                Category.builder().id(1L).name("refrigerator").build()
        ));

        initializer.run(new DefaultApplicationArguments());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Category>> captor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(captor.capture());
        assertEquals(52, captor.getValue().size());
    }

    @Test
    void shouldNotWriteWhenAllAiCategoriesExist() throws Exception {
        List<Category> existingCategories = AiCategoryInitializer.AI_CATEGORY_NAMES.stream()
                .map(name -> Category.builder().name(name).build())
                .toList();
        when(categoryRepository.findAll()).thenReturn(existingCategories);

        initializer.run(new DefaultApplicationArguments());

        verify(categoryRepository, never()).saveAll(anyList());
    }
}
