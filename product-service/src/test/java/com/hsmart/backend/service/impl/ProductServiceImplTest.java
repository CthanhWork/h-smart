package com.hsmart.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.ProductRequestDTO;
import com.hsmart.backend.application.exceptions.OwnershipDeniedException;
import com.hsmart.backend.application.mapper.ProductMapper;
import com.hsmart.backend.application.mapper.ProductNamingSupport;
import com.hsmart.backend.domain.entities.Product;
import com.hsmart.backend.domain.entities.ProductStatus;
import com.hsmart.backend.infrastructure.config.ApplicationProperties;
import com.hsmart.backend.infrastructure.config.StorageProperties;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.infrastructure.persistence.CategoryRepository;
import com.hsmart.backend.infrastructure.persistence.ProductRepository;
import com.hsmart.backend.service.VisionService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private VisionService visionService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductMapper productMapper;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                visionService,
                productRepository,
                categoryRepository,
                new ObjectMapper(),
                new StorageProperties("uploads"),
                new ApplicationProperties("http://localhost:8000"),
                productMapper,
                new ProductNamingSupport()
        );
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void updateProductShouldRejectWhenUserDoesNotOwnProduct() {
        UserContextHolder.setCurrentUserId("another-user");
        Product product = Product.builder()
                .id(10L)
                .title("Desk")
                .description("Used desk")
                .price(BigDecimal.valueOf(100))
                .status(ProductStatus.ACTIVE)
                .sellerId("seller-1")
                .build();

        when(productRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(product));

        ProductRequestDTO request = ProductRequestDTO.builder()
                .title("Updated desk")
                .description("Updated description")
                .price(BigDecimal.valueOf(110))
                .status(ProductStatus.SOLD)
                .build();

        assertThrows(OwnershipDeniedException.class, () -> productService.updateProduct(10L, request));
    }

    @Test
    void deleteProductShouldSoftDeleteOwnedProduct() {
        UserContextHolder.setCurrentUserId("seller-1");
        Product product = Product.builder()
                .id(11L)
                .title("Chair")
                .description("Used chair")
                .price(BigDecimal.valueOf(80))
                .status(ProductStatus.ACTIVE)
                .sellerId("seller-1")
                .build();

        when(productRepository.findByIdAndIsDeletedFalse(11L)).thenReturn(Optional.of(product));

        productService.deleteProduct(11L);

        verify(productRepository, times(1)).save(product);
        Assertions.assertTrue(product.isDeleted());
    }
}
