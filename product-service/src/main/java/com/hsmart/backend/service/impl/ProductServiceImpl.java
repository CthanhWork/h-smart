package com.hsmart.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.DetectionDTO;
import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.dto.PredictResponseDTO;
import com.hsmart.backend.application.dto.ProductRequestDTO;
import com.hsmart.backend.application.dto.ProductResponseDTO;
import com.hsmart.backend.application.dto.ProductSearchEvent;
import com.hsmart.backend.application.dto.ProductSoldEvent;
import com.hsmart.backend.application.dto.ProductStatsResponseDTO;
import com.hsmart.backend.application.dto.UserAddressResponseDTO;
import com.hsmart.backend.application.exceptions.AiServiceTimeoutException;
import com.hsmart.backend.application.exceptions.AiServiceUnavailableException;
import com.hsmart.backend.application.exceptions.CategoryNotFoundException;
import com.hsmart.backend.application.exceptions.FileProcessingException;
import com.hsmart.backend.application.exceptions.MissingUserContextException;
import com.hsmart.backend.application.exceptions.OwnershipDeniedException;
import com.hsmart.backend.application.exceptions.ProductNotFoundException;
import com.hsmart.backend.application.mapper.ProductMapper;
import com.hsmart.backend.application.mapper.ProductNamingSupport;
import com.hsmart.backend.domain.entities.Category;
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
import com.hsmart.backend.service.ProductService;
import com.hsmart.backend.service.UserAddressClient;
import com.hsmart.backend.service.VisionService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private static final String FALLBACK_PRODUCT_TITLE = "Uncategorized Product";
    private static final EnumSet<ProductStatus> PUBLIC_VISIBLE_STATUSES =
            EnumSet.of(ProductStatus.APPROVED, ProductStatus.ACTIVE);
    private static final EnumSet<ProductStatus> SELLER_MANAGEABLE_STATUSES =
            EnumSet.of(ProductStatus.HIDDEN);

    private final VisionService visionService;
    private final ProductRepository productRepository;
    private final ProductLikeRepository productLikeRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final StorageProperties storageProperties;
    private final ApplicationProperties applicationProperties;
    private final ProductMapper productMapper;
    private final ProductNamingSupport productNamingSupport;
    private final ProductEventPublisher productEventPublisher;
    private final UserAddressClient userAddressClient;

    @Override
    public ProductResponseDTO createProduct(
            ProductRequestDTO request,
            List<MultipartFile> images,
            int analysisImageIndex
    ) throws IOException {
        String sellerId = getCurrentUserId();
        List<MultipartFile> normalizedImages = normalizeImages(images);
        validateAnalysisImageIndex(normalizedImages, analysisImageIndex);
        MultipartFile analysisImage = normalizedImages.get(analysisImageIndex);

        Category category = resolveCategory(request.getCategoryId());
        List<DetectionDTO> detections = Collections.emptyList();
        PredictResponseDTO predictResponse = null;
        RuntimeException aiServiceFailure = null;

        try {
            predictResponse = visionService.detectObjects(analysisImage);
            if (predictResponse.getDetections() != null) {
                detections = predictResponse.getDetections();
            }
        } catch (AiServiceUnavailableException | AiServiceTimeoutException exception) {
            aiServiceFailure = exception;
        }

        String aiMetadataJson = objectMapper.writeValueAsString(detections);
        List<String> relativeImageUrls = saveUploadedFiles(normalizedImages);
        String relativeImageUrl = relativeImageUrls.get(0);
        String imageUrlsJson = objectMapper.writeValueAsString(relativeImageUrls);
        String resolvedTitle = resolveCreateTitle(request, predictResponse, detections, aiServiceFailure);
        validateClientManagedStatus(request.getStatus());
        validateNegotiation(request);
        ProductStatus resolvedStatus = ProductStatus.PENDING_REVIEW;

        Product product = productMapper.toEntity(
                request.getDescription(),
                request.getPrice(),
                request.isNegotiable(),
                resolveMinPrice(request),
                resolvedStatus,
                sellerId,
                category,
                resolvedTitle,
                relativeImageUrl,
                imageUrlsJson,
                aiMetadataJson,
                request.isTitleModifiedByUser()
        );

        Product savedProduct = productRepository.save(product);
        publishProductCreatedAfterCommit(savedProduct);
        if (aiServiceFailure != null) {
            log.warn(
                    "Created product {} with PENDING_REVIEW because AI image analysis failed: {}. Root cause: {}",
                    savedProduct.getId(),
                    aiServiceFailure.getMessage(),
                    getRootCauseMessage(aiServiceFailure),
                    aiServiceFailure
            );
        } else if (request.isTitleModifiedByUser()) {
            log.info("Created product {} for seller {} — title modified by user, pending admin review",
                    savedProduct.getId(), savedProduct.getSellerId());
        } else {
            log.info("Created product {} for seller {}", savedProduct.getId(), savedProduct.getSellerId());
        }
        return toProductResponse(savedProduct);
    }

    @Override
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO request, List<MultipartFile> newImages) throws IOException {
        String currentUserId = getCurrentUserId();
        Product product = getActiveProduct(id);
        validateOwnership(product, currentUserId);
        ProductStatus previousStatus = product.getStatus();

        Category category = resolveCategory(request.getCategoryId());
        validateUpdateStatus(request.getStatus());
        validateNegotiation(request);
        product.setTitle(resolveUpdatedTitle(product, request));
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setNegotiable(request.isNegotiable());
        product.setMinPrice(resolveMinPrice(request));
        product.setCategory(category);
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        if (newImages != null && !newImages.isEmpty()) {
            List<MultipartFile> normalizedImages = normalizeImages(newImages);
            deleteStoredImageFiles(product);
            List<String> relativeImageUrls = saveUploadedFiles(normalizedImages);
            product.setImageUrl(relativeImageUrls.get(0));
            product.setImageUrls(objectMapper.writeValueAsString(relativeImageUrls));
        }

        Product savedProduct = productRepository.save(product);
        publishProductUpdatedAfterCommit(savedProduct);
        if (isTransitionToSold(previousStatus, savedProduct.getStatus())) {
            publishProductSoldAfterCommit(savedProduct);
        }
        log.info("Updated product {} for seller {}", savedProduct.getId(), savedProduct.getSellerId());
        return toProductResponse(savedProduct);
    }

    @Override
    public void deleteProduct(Long id) {
        String currentUserId = getCurrentUserId();
        Product product = getActiveProduct(id);
        validateOwnership(product, currentUserId);

        product.setDeleted(true);
        productRepository.save(product);
        deleteStoredImageFiles(product);
        publishProductDeletedAfterCommit(product);
        log.info("Soft deleted product {} for seller {}", product.getId(), product.getSellerId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ProductResponseDTO> getAllProducts(
            String keyword,
            ProductStatus status,
            Long categoryId,
            Pageable pageable
    ) {
        ProductStatus effectiveStatus = status != null ? status : null;
        Page<ProductResponseDTO> page = productRepository
                .findAll(buildPublicProductSpecification(normalizeKeyword(keyword), effectiveStatus, categoryId), pageable)
                .map(this::toProductResponse);
        return PageResponseDTO.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ProductResponseDTO> getMyProducts(Pageable pageable) {
        String sellerId = getCurrentUserId();
        Page<ProductResponseDTO> page = productRepository
                .findAllBySellerIdAndIsDeletedFalse(sellerId, pageable)
                .map(this::toProductResponse);
        return PageResponseDTO.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ProductResponseDTO> getWishlist(Pageable pageable) {
        String userId = getCurrentUserId();
        Page<ProductResponseDTO> page = productLikeRepository
                .findAllByUserIdAndProductIsDeletedFalseAndProductStatusIn(userId, PUBLIC_VISIBLE_STATUSES, pageable)
                .map(productLike -> toProductResponse(productLike.getProduct()));
        return PageResponseDTO.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {
        Product product = getPublicProduct(id);
        return toProductResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getProductByIdForAdmin(Long id) {
        Product product = getActiveProduct(id);
        return toProductResponse(product);
    }

    @Override
    public String toggleProductLike(Long id) {
        String userId = getCurrentUserId();
        Product product = getPublicProduct(id);
        return productLikeRepository.findByUserIdAndProductId(userId, product.getId())
                .map(productLike -> removeProductLike(productLike, product))
                .orElseGet(() -> saveProductLike(userId, product));
    }

    @Override
    public void markProductSoldFromOrderEvent(Long productId) {
        Product product = getActiveProduct(productId);
        ProductStatus previousStatus = product.getStatus();

        if (previousStatus == ProductStatus.SOLD) {
            log.info("Skipped order completion product update because product {} is already SOLD", productId);
            return;
        }

        product.setStatus(ProductStatus.SOLD);
        Product savedProduct = productRepository.save(product);
        publishProductUpdatedAfterCommit(savedProduct);
        publishProductSoldAfterCommit(savedProduct);
        log.info("Marked product {} as SOLD from order completion event", savedProduct.getId());
    }

    @Override
    public ProductResponseDTO updateModerationStatus(Long id, ProductStatus status) {
        Product product = getActiveProduct(id);
        ProductStatus previousStatus = product.getStatus();

        if (shouldSkipModerationDowngrade(previousStatus, status)) {
            log.info(
                    "Skipped moderation status downgrade for product {} from {} to {}",
                    product.getId(),
                    previousStatus,
                    status
            );
            return toProductResponse(product);
        }

        product.setStatus(status);
        Product savedProduct = productRepository.save(product);
        publishProductUpdatedAfterCommit(savedProduct);
        if (isTransitionToSold(previousStatus, savedProduct.getStatus())) {
            publishProductSoldAfterCommit(savedProduct);
        }

        log.info("Updated product {} moderation status to {}", savedProduct.getId(), savedProduct.getStatus());
        return toProductResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductStatsResponseDTO getProductStats() {
        return ProductStatsResponseDTO.builder()
                .totalSellingProducts(productRepository.countByIsDeletedFalseAndStatusIn(
                        EnumSet.of(ProductStatus.APPROVED, ProductStatus.ACTIVE)))
                .totalPendingReview(productRepository.countByIsDeletedFalseAndStatusIn(
                        EnumSet.of(ProductStatus.PENDING_REVIEW)))
                .totalHidden(productRepository.countByIsDeletedFalseAndStatusIn(
                        EnumSet.of(ProductStatus.HIDDEN)))
                .totalSold(productRepository.countByIsDeletedFalseAndStatusIn(
                        EnumSet.of(ProductStatus.SOLD)))
                .build();
    }

    private String getCurrentUserId() {
        return UserContextHolder.getCurrentUserId()
                .orElseThrow(MissingUserContextException::new);
    }

    private String saveProductLike(String userId, Product product) {
        productLikeRepository.save(ProductLike.builder()
                .userId(userId)
                .product(product)
                .build());
        productRepository.incrementLikeCount(product.getId());
        log.info("Saved product {} to wishlist for user {}", product.getId(), userId);
        return "Product saved to wishlist";
    }

    private String removeProductLike(ProductLike productLike, Product product) {
        productLikeRepository.delete(productLike);
        productRepository.decrementLikeCount(product.getId());
        log.info("Removed product {} from wishlist for user {}", product.getId(), productLike.getUserId());
        return "Product removed from wishlist";
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }

    private Specification<Product> buildPublicProductSpecification(
            String keyword,
            ProductStatus requestedStatus,
            Long categoryId
    ) {
        Specification<Product> specification =
                (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("isDeleted"));

        if (requestedStatus != null) {
            specification = specification.and(
                    (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), requestedStatus)
            );
        } else {
            specification = specification.and(
                    (root, query, criteriaBuilder) -> root.get("status").in(PUBLIC_VISIBLE_STATUSES)
            );
        }

        if (categoryId != null) {
            specification = specification.and(
                    (root, query, criteriaBuilder) ->
                            criteriaBuilder.equal(root.join("category").get("id"), categoryId)
            );
        }

        if (keyword != null) {
            String pattern = "%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%";
            specification = specification.and((root, query, criteriaBuilder) -> {
                var category = root.join("category", jakarta.persistence.criteria.JoinType.LEFT);
                return criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(category.get("name")), pattern)
                );
            });
        }

        return specification;
    }

    private Product getActiveProduct(Long id) {
        return productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Product getPublicProduct(Long id) {
        Product product = getActiveProduct(id);
        if (!PUBLIC_VISIBLE_STATUSES.contains(product.getStatus())) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }

    private void validateOwnership(Product product, String currentUserId) {
        if (!product.getSellerId().equals(currentUserId)) {
            throw new OwnershipDeniedException();
        }
    }

    private String resolveCreateTitle(
            ProductRequestDTO request,
            PredictResponseDTO predictResponse,
            List<DetectionDTO> detections,
            RuntimeException aiServiceFailure
    ) {
        if (StringUtils.hasText(request.getTitle())) {
            return request.getTitle().trim();
        }
        // AI is down (not "recognized nothing") — keep a degraded fallback so sellers can still list.
        if (aiServiceFailure != null) {
            return FALLBACK_PRODUCT_TITLE;
        }
        // AI ran but recognized nothing: never auto-assign a name, ask the seller to provide one.
        if (productNamingSupport.isUnrecognized(predictResponse)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không nhận diện được sản phẩm trong ảnh. Vui lòng nhập tên sản phẩm."
            );
        }
        return productNamingSupport.resolveTitle(request.getTitle(), detections);
    }

    private String resolveUpdatedTitle(Product product, ProductRequestDTO request) {
        if (StringUtils.hasText(request.getTitle())) {
            return request.getTitle().trim();
        }
        return product.getTitle();
    }

    private boolean isTransitionToSold(ProductStatus previousStatus, ProductStatus currentStatus) {
        return previousStatus != ProductStatus.SOLD && currentStatus == ProductStatus.SOLD;
    }

    private boolean shouldSkipModerationDowngrade(ProductStatus previousStatus, ProductStatus requestedStatus) {
        return requestedStatus == ProductStatus.PENDING_REVIEW
                && (previousStatus == ProductStatus.APPROVED || previousStatus == ProductStatus.SOLD);
    }

    private String getRootCauseMessage(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return StringUtils.hasText(rootCause.getMessage())
                ? rootCause.getMessage()
                : rootCause.getClass().getSimpleName();
    }

    private void validateClientManagedStatus(ProductStatus status) {
        if (status != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product status cannot be set during product creation"
            );
        }
    }

    private void validateUpdateStatus(ProductStatus status) {
        if (status != null && !SELLER_MANAGEABLE_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product status can only be changed by admin-service or order-service workflows"
            );
        }
    }

    private void validateNegotiation(ProductRequestDTO request) {
        if (!request.isNegotiable()) {
            return;
        }
        BigDecimal minPrice = request.getMinPrice();
        if (minPrice == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Giá sàn (minPrice) là bắt buộc khi cho phép trả giá"
            );
        }
        if (minPrice.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá sàn phải lớn hơn 0");
        }
        if (request.getPrice() != null && minPrice.compareTo(request.getPrice()) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá sàn phải nhỏ hơn giá niêm yết");
        }
    }

    private BigDecimal resolveMinPrice(ProductRequestDTO request) {
        return request.isNegotiable() ? request.getMinPrice() : null;
    }

    private void deleteStoredImageFiles(Product product) {
        List<String> relativeUrls = parseImageUrls(product.getId(), product.getImageUrls());
        if (relativeUrls.isEmpty() && StringUtils.hasText(product.getImageUrl())) {
            relativeUrls = List.of(product.getImageUrl());
        }
        Path uploadDir = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();
        for (String relativeUrl : relativeUrls) {
            String filename = relativeUrl.substring(relativeUrl.lastIndexOf('/') + 1);
            try {
                Files.deleteIfExists(uploadDir.resolve(filename));
            } catch (IOException e) {
                log.warn("Failed to delete image file {} for product {}: {}", filename, product.getId(), e.getMessage());
            }
        }
    }

    private void publishProductCreatedAfterCommit(Product product) {
        ProductSearchEvent event = toProductSearchEvent(product);
        publishAfterCommit(() -> productEventPublisher.publishProductCreated(event));
    }

    private void publishProductUpdatedAfterCommit(Product product) {
        ProductSearchEvent event = toProductSearchEvent(product);
        publishAfterCommit(() -> productEventPublisher.publishProductUpdated(event));
    }

    private void publishProductDeletedAfterCommit(Product product) {
        ProductSearchEvent event = toProductSearchEvent(product);
        publishAfterCommit(() -> productEventPublisher.publishProductDeleted(event));
    }

    private void publishProductSoldAfterCommit(Product product) {
        ProductSoldEvent event = ProductSoldEvent.builder()
                .productId(product.getId())
                .sellerId(product.getSellerId())
                .title(product.getTitle())
                .build();

        publishAfterCommit(() -> productEventPublisher.publishProductSold(event));
    }

    private void publishAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private ProductSearchEvent toProductSearchEvent(Product product) {
        return ProductSearchEvent.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .sellerId(product.getSellerId())
                .imageUrl(product.getImageUrl())
                .aiMetadata(parseAiMetadata(product.getId(), product.getAiMetadata()))
                .build();
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private List<MultipartFile> normalizeImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one image file is required");
        }

        List<MultipartFile> normalizedImages = images.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (normalizedImages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one image file is required");
        }
        return normalizedImages;
    }

    private void validateAnalysisImageIndex(List<MultipartFile> images, int analysisImageIndex) {
        if (analysisImageIndex < 0 || analysisImageIndex >= images.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "analysisImageIndex must reference one of the uploaded images"
            );
        }
    }

    private List<String> saveUploadedFiles(List<MultipartFile> files) {
        List<String> relativeImageUrls = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            relativeImageUrls.add(saveUploadedFile(file));
        }
        return relativeImageUrls;
    }

    private String saveUploadedFile(MultipartFile file) {
        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "upload.jpg";

        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            extension = originalFilename.substring(lastDotIndex);
        }

        String storedFilename = UUID.randomUUID() + extension;
        Path uploadDir = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();
        Path targetPath = uploadDir.resolve(storedFilename);

        try {
            Files.createDirectories(uploadDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new FileProcessingException("Unable to store uploaded image", exception);
        }

        return "api/v1/products/media/" + storedFilename;
    }

    private ProductResponseDTO toProductResponse(Product product) {
        List<DetectionDTO> aiMetadata = parseAiMetadata(product.getId(), product.getAiMetadata());
        List<String> imageUrls = parseImageUrls(product.getId(), product.getImageUrls());
        ProductResponseDTO response = productMapper.toResponse(product, aiMetadata, applicationProperties.publicBaseUrl());
        response.setImageUrls(toAbsoluteImageUrls(imageUrls, response.getImageUrl(), applicationProperties.publicBaseUrl()));
        userAddressClient.getUserAddress(product.getSellerId())
                .ifPresent(address -> enrichSellerLocation(response, address));
        return response;
    }

    private void enrichSellerLocation(ProductResponseDTO response, UserAddressResponseDTO address) {
        response.setSellerDistrict(address.district());
        response.setSellerProvince(address.province());
    }

    private List<DetectionDTO> parseAiMetadata(Long productId, String aiMetadataJson) {
        if (!StringUtils.hasText(aiMetadataJson)) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(aiMetadataJson, new TypeReference<List<DetectionDTO>>() {
            });
        } catch (IOException exception) {
            log.warn(
                    "Ignored invalid stored AI metadata for product {}. Manual review data will be returned without detections",
                    productId,
                    exception
            );
            return Collections.emptyList();
        }
    }

    private List<String> parseImageUrls(Long productId, String imageUrlsJson) {
        if (!StringUtils.hasText(imageUrlsJson)) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(imageUrlsJson, new TypeReference<List<String>>() {
            });
        } catch (IOException exception) {
            log.warn(
                    "Ignored invalid stored image gallery metadata for product {}. Only the primary image will be returned",
                    productId,
                    exception
            );
            return Collections.emptyList();
        }
    }

    private List<String> toAbsoluteImageUrls(List<String> imageUrls, String primaryImageUrl, String publicBaseUrl) {
        List<String> relativeImageUrls = imageUrls == null ? Collections.emptyList() : imageUrls.stream()
                .filter(StringUtils::hasText)
                .toList();
        if (relativeImageUrls.isEmpty() && StringUtils.hasText(primaryImageUrl)) {
            return List.of(primaryImageUrl);
        }

        return relativeImageUrls.stream()
                .map(imageUrl -> productMapper.toAbsoluteImageUrl(publicBaseUrl, imageUrl))
                .toList();
    }
}
