package com.hsmart.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hsmart.backend.application.dto.ProductDTO;
import com.hsmart.backend.application.dto.DetectionDTO;
import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.dto.ProductResponseDTO;
import com.hsmart.backend.application.dto.PredictResponseDTO;
import com.hsmart.backend.application.exceptions.FileProcessingException;
import com.hsmart.backend.application.exceptions.ProductNotFoundException;
import com.hsmart.backend.application.mapper.ProductMapper;
import com.hsmart.backend.domain.entities.Product;
import com.hsmart.backend.infrastructure.config.ApplicationProperties;
import com.hsmart.backend.infrastructure.config.StorageProperties;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final VisionService visionService;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;
    private final StorageProperties storageProperties;
    private final ApplicationProperties applicationProperties;

    @Override
    public ProductResponseDTO createProduct(ProductDTO productDto, MultipartFile file) throws IOException {
        PredictResponseDTO predictResponse = visionService.detectObjects(file);
        String aiMetadataJson = objectMapper.writeValueAsString(predictResponse.getDetections());
        String savedImagePath = saveUploadedFile(file);
        String resolvedName = ProductMapper.resolveProductName(productDto.getName(), predictResponse.getDetections());

        Product product = ProductMapper.toEntity(productDto, resolvedName, savedImagePath, aiMetadataJson);

        Product savedProduct = productRepository.save(product);

        return toProductResponse(savedProduct);
    }

    @Override
    public PageResponseDTO<ProductResponseDTO> getAllProducts(Pageable pageable) {
        Page<ProductResponseDTO> page = productRepository.findAll(pageable)
                .map(this::toProductResponse);
        return PageResponseDTO.from(page);
    }

    @Override
    public ProductResponseDTO getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return toProductResponse(product);
    }

    @Override
    public ProductResponseDTO updateProduct(Long id, ProductDTO productDto, MultipartFile image) throws IOException {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        String imageUrl = existingProduct.getImageUrl();
        String aiMetadataJson = existingProduct.getAiMetadata();
        List<DetectionDTO> detections = parseAiMetadata(existingProduct.getAiMetadata());

        if (image != null && !image.isEmpty()) {
            PredictResponseDTO predictResponse = visionService.detectObjects(image);
            detections = predictResponse.getDetections();
            aiMetadataJson = objectMapper.writeValueAsString(detections);

            String newImagePath = saveUploadedFile(image);
            deleteUploadedFile(existingProduct.getImageUrl());
            imageUrl = newImagePath;
        }

        existingProduct.setName(resolveUpdatedName(existingProduct.getName(), productDto.getName(), detections, image));
        existingProduct.setDescription(productDto.getDescription() != null ? productDto.getDescription() : existingProduct.getDescription());
        existingProduct.setPrice(productDto.getPrice() != null ? productDto.getPrice() : existingProduct.getPrice());
        existingProduct.setImageUrl(imageUrl);
        existingProduct.setAiMetadata(aiMetadataJson);

        Product updatedProduct = productRepository.save(existingProduct);
        return toProductResponse(updatedProduct);
    }

    @Override
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        deleteUploadedFile(product.getImageUrl());
        productRepository.delete(product);
    }

    private String saveUploadedFile(MultipartFile file) {
        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? StringUtils.cleanPath(file.getOriginalFilename())
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

        return uploadDir.getFileName() + "/" + storedFilename;
    }

    private ProductResponseDTO toProductResponse(Product product) {
        List<DetectionDTO> aiMetadata = parseAiMetadata(product.getAiMetadata());
        return ProductMapper.toResponse(product, aiMetadata, applicationProperties.publicBaseUrl());
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

    private String resolveUpdatedName(String existingName, String requestedName, List<DetectionDTO> detections, MultipartFile image) {
        if (StringUtils.hasText(requestedName)) {
            return requestedName.trim();
        }

        if (StringUtils.hasText(existingName)) {
            return existingName;
        }

        if (image != null && !image.isEmpty()) {
            return ProductMapper.resolveProductName(existingName, detections);
        }

        return existingName;
    }

    private void deleteUploadedFile(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return;
        }

        Path uploadRoot = Paths.get(storageProperties.uploadDir()).toAbsolutePath().normalize();
        Path storageBase = uploadRoot.getParent() != null ? uploadRoot.getParent() : uploadRoot;
        Path imagePath = Paths.get(imageUrl).isAbsolute()
                ? Paths.get(imageUrl)
                : storageBase.resolve(imageUrl).normalize();

        try {
            Files.deleteIfExists(imagePath);
        } catch (IOException exception) {
            throw new FileProcessingException("Unable to delete stored image", exception);
        }
    }
}
