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
import com.hsmart.backend.application.exceptions.CategoryNotFoundException;
import com.hsmart.backend.application.exceptions.FileProcessingException;
import com.hsmart.backend.application.exceptions.MissingUserContextException;
import com.hsmart.backend.application.exceptions.OwnershipDeniedException;
import com.hsmart.backend.application.exceptions.ProductNotFoundException;
import com.hsmart.backend.application.mapper.ProductMapper;
import com.hsmart.backend.application.mapper.ProductNamingSupport;
import com.hsmart.backend.domain.entities.Category;
import com.hsmart.backend.domain.entities.Product;
import com.hsmart.backend.domain.entities.ProductStatus;
import com.hsmart.backend.infrastructure.config.ApplicationProperties;
import com.hsmart.backend.infrastructure.config.StorageProperties;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.infrastructure.messaging.ProductEventPublisher;
import com.hsmart.backend.infrastructure.persistence.CategoryRepository;
import com.hsmart.backend.infrastructure.persistence.ProductRepository;
import com.hsmart.backend.service.ProductService;
import com.hsmart.backend.service.VisionService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final VisionService visionService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final StorageProperties storageProperties;
    private final ApplicationProperties applicationProperties;
    private final ProductMapper productMapper;
    private final ProductNamingSupport productNamingSupport;
    private final ProductEventPublisher productEventPublisher;

    @Override
    public ProductResponseDTO createProduct(ProductRequestDTO request, MultipartFile image) throws IOException {
        String sellerId = getCurrentUserId();

        Category category = resolveCategory(request.getCategoryId());
        PredictResponseDTO predictResponse = visionService.detectObjects(image);
        String aiMetadataJson = objectMapper.writeValueAsString(predictResponse.getDetections());
        String relativeImageUrl = saveUploadedFile(image);
        String resolvedTitle = productNamingSupport.resolveTitle(request.getTitle(), predictResponse.getDetections());
        ProductStatus resolvedStatus = request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE;

        Product product = productMapper.toEntity(
                request.getDescription(),
                request.getPrice(),
                resolvedStatus,
                sellerId,
                category,
                resolvedTitle,
                relativeImageUrl,
                aiMetadataJson
        );

        Product savedProduct = productRepository.save(product);
        publishProductCreatedAfterCommit(savedProduct);
        log.info("Created product {} for seller {}", savedProduct.getId(), savedProduct.getSellerId());
        return toProductResponse(savedProduct);
    }

    @Override
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO request) {
        String currentUserId = getCurrentUserId();
        Product product = getActiveProduct(id);
        validateOwnership(product, currentUserId);
        ProductStatus previousStatus = product.getStatus();

        Category category = resolveCategory(request.getCategoryId());
        product.setTitle(resolveUpdatedTitle(product, request));
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStatus(request.getStatus() != null ? request.getStatus() : product.getStatus());
        product.setCategory(category);

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
        Page<ProductResponseDTO> page = productRepository.searchProducts(normalizeKeyword(keyword), status, categoryId, pageable)
                .map(this::toProductResponse);
        return PageResponseDTO.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {
        Product product = getActiveProduct(id);
        return toProductResponse(product);
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

    private String getCurrentUserId() {
        return UserContextHolder.getCurrentUserId()
                .orElseThrow(MissingUserContextException::new);
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }

    private Product getActiveProduct(Long id) {
        return productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private void validateOwnership(Product product, String currentUserId) {
        if (!product.getSellerId().equals(currentUserId)) {
            throw new OwnershipDeniedException();
        }
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

    private void publishProductCreatedAfterCommit(Product product) {
        ProductSearchEvent event = toProductSearchEvent(product);
        publishAfterCommit(() -> productEventPublisher.publishProductCreated(event));
    }

    private void publishProductUpdatedAfterCommit(Product product) {
        ProductSearchEvent event = toProductSearchEvent(product);
        publishAfterCommit(() -> productEventPublisher.publishProductUpdated(event));
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
                .build();
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
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
        List<DetectionDTO> aiMetadata = parseAiMetadata(product.getAiMetadata());
        return productMapper.toResponse(product, aiMetadata, applicationProperties.publicBaseUrl());
    }

    private List<DetectionDTO> parseAiMetadata(String aiMetadataJson) {
        if (!StringUtils.hasText(aiMetadataJson)) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(aiMetadataJson, new TypeReference<List<DetectionDTO>>() {
            });
        } catch (IOException exception) {
            throw new FileProcessingException("Unable to parse stored AI metadata", exception);
        }
    }
}
