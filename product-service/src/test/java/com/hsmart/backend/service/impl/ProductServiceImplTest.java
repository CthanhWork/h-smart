package com.hsmart.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.DetectionDTO;
import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.dto.PredictResponseDTO;
import com.hsmart.backend.application.dto.ProductRequestDTO;
import com.hsmart.backend.application.dto.ProductResponseDTO;
import com.hsmart.backend.application.exceptions.AiServiceUnavailableException;
import com.hsmart.backend.application.exceptions.OwnershipDeniedException;
import com.hsmart.backend.application.mapper.ProductMapper;
import com.hsmart.backend.application.mapper.ProductNamingSupport;
import com.hsmart.backend.domain.entities.Product;
import com.hsmart.backend.domain.entities.ProductLike;
import com.hsmart.backend.domain.entities.ProductStatus;
import com.hsmart.backend.infrastructure.config.ApplicationProperties;
import com.hsmart.backend.infrastructure.config.StorageProperties;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.infrastructure.messaging.ProductEventPublisher;
import com.hsmart.backend.infrastructure.persistence.CategoryRepository;
import com.hsmart.backend.infrastructure.persistence.ProductLikeRepository;
import com.hsmart.backend.infrastructure.persistence.ProductRepository;
import com.hsmart.backend.presentation.controllers.ProductController;
import com.hsmart.backend.service.ProductService;
import com.hsmart.backend.service.UserAddressClient;
import com.hsmart.backend.service.VisionService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @TempDir
    private Path uploadDirectory;

    @Mock
    private VisionService visionService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductLikeRepository productLikeRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductEventPublisher productEventPublisher;

    @Mock
    private UserAddressClient userAddressClient;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                visionService,
                productRepository,
                productLikeRepository,
                categoryRepository,
                new ObjectMapper(),
                new StorageProperties(uploadDirectory.toString()),
                new ApplicationProperties("http://localhost:8000"),
                productMapper,
                new ProductNamingSupport(),
                productEventPublisher,
                userAddressClient
        );
        org.mockito.Mockito.lenient()
                .when(userAddressClient.getUserAddress(anyString()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient()
                .when(productMapper.toResponse(any(Product.class), anyList(), eq("http://localhost:8000")))
                .thenAnswer(invocation -> {
                    Product product = invocation.getArgument(0);
                    return ProductResponseDTO.builder()
                            .id(product.getId())
                            .title(product.getTitle())
                            .description(product.getDescription())
                            .price(product.getPrice())
                            .status(product.getStatus())
                            .sellerId(product.getSellerId())
                            .imageUrl("http://localhost:8000/api/v1/products/media/mock.jpg")
                            .build();
                });
        org.mockito.Mockito.lenient()
                .when(productMapper.toAbsoluteImageUrl(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0) + "/" + invocation.getArgument(1));
    }

    @Test
    void createProductShouldRequireManualReviewWhenAiServiceIsUnavailable() throws IOException {
        UserContextHolder.setCurrentUserId("seller-1");
        MockMultipartFile image = new MockMultipartFile(
                "files",
                "desk.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );
        ProductRequestDTO request = ProductRequestDTO.builder()
                .title(" ")
                .description("Used desk")
                .price(BigDecimal.valueOf(100))
                .build();
        Product pendingProduct = Product.builder()
                .id(25L)
                .title("Uncategorized Product")
                .description("Used desk")
                .price(BigDecimal.valueOf(100))
                .status(ProductStatus.PENDING_REVIEW)
                .sellerId("seller-1")
                .imageUrls("[\"api/v1/products/media/mock.jpg\"]")
                .aiMetadata("[]")
                .build();
        ProductResponseDTO response = ProductResponseDTO.builder()
                .id(25L)
                .title("Uncategorized Product")
                .status(ProductStatus.PENDING_REVIEW)
                .aiMetadata(List.of())
                .build();

        when(visionService.detectObjects(image)).thenThrow(
                new AiServiceUnavailableException("AI service is unavailable", new IOException("Connection refused"))
        );
        when(productMapper.toEntity(
                eq("Used desk"),
                eq(BigDecimal.valueOf(100)),
                eq(ProductStatus.PENDING_REVIEW),
                eq("seller-1"),
                eq(null),
                eq("Uncategorized Product"),
                argThat(imageUrl -> imageUrl.startsWith("api/v1/products/media/")),
                argThat(imageUrlsJson -> imageUrlsJson.contains("api/v1/products/media/")),
                eq("[]")
        )).thenReturn(pendingProduct);
        when(productRepository.save(pendingProduct)).thenReturn(pendingProduct);
        when(productMapper.toResponse(eq(pendingProduct), anyList(), eq("http://localhost:8000")))
                .thenReturn(response);

        ProductResponseDTO result = productService.createProduct(request, List.of(image), 0);

        Assertions.assertEquals(ProductStatus.PENDING_REVIEW, result.getStatus());
        Assertions.assertEquals("Uncategorized Product", result.getTitle());
        Assertions.assertTrue(result.getAiMetadata().isEmpty());
        Assertions.assertEquals(1, result.getImageUrls().size());
        verify(productRepository).save(pendingProduct);
        verify(productEventPublisher).publishProductCreated(argThat(event ->
                event.getId().equals(25L)
                        && event.getStatus().equals("PENDING_REVIEW")
                        && event.getAiMetadata().isEmpty()
        ));
        verify(productMapper).toEntity(
                eq("Used desk"),
                eq(BigDecimal.valueOf(100)),
                eq(ProductStatus.PENDING_REVIEW),
                eq("seller-1"),
                eq(null),
                eq("Uncategorized Product"),
                any(String.class),
                any(String.class),
                eq("[]")
        );
    }

    @Test
    void createProductControllerShouldReturnManualReviewMessageForAiFallback() throws IOException {
        ProductService productServiceMock = mock(ProductService.class);
        ProductController controller = new ProductController(productServiceMock, null, null);
        ProductRequestDTO request = ProductRequestDTO.builder()
                .price(BigDecimal.valueOf(100))
                .build();
        MockMultipartFile image = new MockMultipartFile(
                "files",
                "desk.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );
        ProductResponseDTO product = ProductResponseDTO.builder()
                .id(25L)
                .title("Uncategorized Product")
                .status(ProductStatus.PENDING_REVIEW)
                .build();
        when(productServiceMock.createProduct(request, List.of(image), 0)).thenReturn(product);

        ResponseEntity<ApiResponse<ProductResponseDTO>> response = controller.createProduct(request, List.of(image), null, 0);

        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Assertions.assertNotNull(response.getBody());
        Assertions.assertEquals(
                "Product submitted successfully and is pending moderation review.",
                response.getBody().getMessage()
        );
        Assertions.assertEquals(product, response.getBody().getData());
        verify(productServiceMock).createProduct(request, List.of(image), 0);
    }

    @Test
    void createProductShouldAnalyzeOnlySelectedImageAndPersistFullGallery() throws IOException {
        UserContextHolder.setCurrentUserId("seller-1");
        MockMultipartFile firstImage = new MockMultipartFile(
                "files",
                "front.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );
        MockMultipartFile secondImage = new MockMultipartFile(
                "files",
                "detail.jpg",
                "image/jpeg",
                new byte[]{4, 5, 6}
        );
        ProductRequestDTO request = ProductRequestDTO.builder()
                .title(" ")
                .description("Clean used chair")
                .price(BigDecimal.valueOf(250000))
                .build();
        PredictResponseDTO prediction = PredictResponseDTO.builder()
                .detections(List.of(DetectionDTO.builder()
                        .label("chair")
                        .score(0.91)
                        .build()))
                .build();

        when(visionService.detectObjects(secondImage)).thenReturn(prediction);
        when(productMapper.toEntity(
                eq("Clean used chair"),
                eq(BigDecimal.valueOf(250000)),
                eq(ProductStatus.PENDING_REVIEW),
                eq("seller-1"),
                eq(null),
                eq("Ghe"),
                anyString(),
                anyString(),
                anyString()
        )).thenAnswer(invocation -> Product.builder()
                .id(30L)
                .title(invocation.getArgument(5))
                .description(invocation.getArgument(0))
                .price(invocation.getArgument(1))
                .status(invocation.getArgument(2))
                .sellerId(invocation.getArgument(3))
                .imageUrl(invocation.getArgument(6))
                .imageUrls(invocation.getArgument(7))
                .aiMetadata(invocation.getArgument(8))
                .build());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponseDTO result = productService.createProduct(request, List.of(firstImage, secondImage), 1);

        verify(visionService).detectObjects(secondImage);
        verify(visionService, org.mockito.Mockito.never()).detectObjects(firstImage);
        Assertions.assertEquals(ProductStatus.PENDING_REVIEW, result.getStatus());
        Assertions.assertEquals("Ghe", result.getTitle());
        Assertions.assertEquals(2, result.getImageUrls().size());
        Assertions.assertTrue(result.getImageUrls().stream().allMatch(url ->
                url.startsWith("http://localhost:8000/api/v1/products/media/")));
        verify(productEventPublisher).publishProductCreated(argThat(event ->
                event.getId().equals(30L)
                        && event.getStatus().equals("PENDING_REVIEW")
                        && event.getAiMetadata().size() == 1
                        && event.getAiMetadata().get(0).getLabel().equals("chair")
        ));
    }

    @Test
    void createProductShouldRejectInvalidAnalysisImageIndexBeforeCallingAi() {
        UserContextHolder.setCurrentUserId("seller-1");
        MockMultipartFile image = new MockMultipartFile(
                "files",
                "front.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );
        ProductRequestDTO request = ProductRequestDTO.builder()
                .title("Chair")
                .description("Clean used chair")
                .price(BigDecimal.valueOf(250000))
                .build();

        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> productService.createProduct(request, List.of(image), 1)
        );

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        Assertions.assertEquals("analysisImageIndex must reference one of the uploaded images", exception.getReason());
        verify(visionService, org.mockito.Mockito.never()).detectObjects(any());
        verify(productRepository, org.mockito.Mockito.never()).save(any());
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

    @Test
    void updateProductShouldRejectClientManagedStatusChanges() {
        UserContextHolder.setCurrentUserId("seller-1");
        Product product = Product.builder()
                .id(12L)
                .title("Microwave")
                .description("Used microwave")
                .price(BigDecimal.valueOf(120))
                .status(ProductStatus.ACTIVE)
                .sellerId("seller-1")
                .build();

        when(productRepository.findByIdAndIsDeletedFalse(12L)).thenReturn(Optional.of(product));

        ProductRequestDTO request = ProductRequestDTO.builder()
                .title("Microwave")
                .description("Ready to pick up")
                .price(BigDecimal.valueOf(120))
                .status(ProductStatus.SOLD)
                .build();

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> productService.updateProduct(12L, request));
    }

    @Test
    void updateModerationStatusShouldNotDowngradeApprovedProductToPendingReview() {
        Product product = Product.builder()
                .id(16L)
                .title("Approved chair")
                .description("Clean chair")
                .price(BigDecimal.valueOf(250000))
                .status(ProductStatus.APPROVED)
                .sellerId("seller-1")
                .aiMetadata("[]")
                .imageUrls("[]")
                .build();

        when(productRepository.findByIdAndIsDeletedFalse(16L)).thenReturn(Optional.of(product));

        ProductResponseDTO result = productService.updateModerationStatus(16L, ProductStatus.PENDING_REVIEW);

        Assertions.assertEquals(ProductStatus.APPROVED, product.getStatus());
        Assertions.assertEquals(ProductStatus.APPROVED, result.getStatus());
        verify(productRepository, never()).save(any(Product.class));
        verify(productEventPublisher, never()).publishProductUpdated(any());
    }

    @Test
    void updateModerationStatusShouldStillAllowManualHideAfterApproval() {
        Product product = Product.builder()
                .id(17L)
                .title("Approved desk")
                .description("Clean desk")
                .price(BigDecimal.valueOf(350000))
                .status(ProductStatus.APPROVED)
                .sellerId("seller-1")
                .aiMetadata("[]")
                .imageUrls("[]")
                .build();

        when(productRepository.findByIdAndIsDeletedFalse(17L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponseDTO result = productService.updateModerationStatus(17L, ProductStatus.HIDDEN);

        Assertions.assertEquals(ProductStatus.HIDDEN, product.getStatus());
        Assertions.assertEquals(ProductStatus.HIDDEN, result.getStatus());
        verify(productRepository).save(product);
        verify(productEventPublisher).publishProductUpdated(argThat(event ->
                event.getId().equals(17L) && event.getStatus().equals("HIDDEN")
        ));
    }

    @Test
    void toggleProductLikeShouldSaveProductWhenItIsNotInWishlist() {
        UserContextHolder.setCurrentUserId("user-1");
        Product product = Product.builder()
                .id(13L)
                .title("Chair")
                .status(ProductStatus.ACTIVE)
                .sellerId("seller-1")
                .build();
        when(productRepository.findByIdAndIsDeletedFalse(13L)).thenReturn(Optional.of(product));
        when(productLikeRepository.findByUserIdAndProductId("user-1", 13L)).thenReturn(Optional.empty());

        String message = productService.toggleProductLike(13L);

        verify(productLikeRepository).save(argThat(productLike ->
                productLike.getUserId().equals("user-1") && productLike.getProduct().equals(product)
        ));
        verify(productRepository).incrementLikeCount(13L);
        Assertions.assertEquals("Product saved to wishlist", message);
    }

    @Test
    void toggleProductLikeShouldRemoveExistingWishlistItem() {
        UserContextHolder.setCurrentUserId("user-1");
        Product product = Product.builder()
                .id(14L)
                .title("Chair")
                .status(ProductStatus.ACTIVE)
                .sellerId("seller-1")
                .build();
        ProductLike productLike = ProductLike.builder()
                .id(21L)
                .userId("user-1")
                .product(product)
                .build();
        when(productRepository.findByIdAndIsDeletedFalse(14L)).thenReturn(Optional.of(product));
        when(productLikeRepository.findByUserIdAndProductId("user-1", 14L)).thenReturn(Optional.of(productLike));

        String message = productService.toggleProductLike(14L);

        verify(productLikeRepository).delete(productLike);
        verify(productRepository).decrementLikeCount(14L);
        Assertions.assertEquals("Product removed from wishlist", message);
    }

    @Test
    void getWishlistShouldExcludeSoldAndDeletedProductsThroughRepositoryQuery() {
        UserContextHolder.setCurrentUserId("user-1");
        Product product = Product.builder()
                .id(15L)
                .title("Chair")
                .status(ProductStatus.ACTIVE)
                .sellerId("seller-1")
                .aiMetadata("[]")
                .build();
        ProductResponseDTO response = ProductResponseDTO.builder()
                .id(15L)
                .title("Chair")
                .likeCount(2)
                .build();
        ProductLike productLike = ProductLike.builder()
                .id(22L)
                .userId("user-1")
                .product(product)
                .build();
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(productLikeRepository.findAllByUserIdAndProductIsDeletedFalseAndProductStatusNot(
                "user-1",
                ProductStatus.SOLD,
                pageable
        )).thenReturn(new PageImpl<>(List.of(productLike), pageable, 1));
        when(productMapper.toResponse(eq(product), anyList(), eq("http://localhost:8000"))).thenReturn(response);

        PageResponseDTO<ProductResponseDTO> wishlist = productService.getWishlist(pageable);

        Assertions.assertEquals(List.of(response), wishlist.getContent());
        Assertions.assertEquals(1, wishlist.getTotalElements());
    }
}
