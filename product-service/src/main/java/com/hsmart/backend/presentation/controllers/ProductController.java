package com.hsmart.backend.presentation.controllers;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.ImageAnalysisResponseDTO;
import com.hsmart.backend.application.dto.PageResponseDTO;
import com.hsmart.backend.application.dto.ProductListingSuggestionResponseDTO;
import com.hsmart.backend.application.dto.ProductModerationStatusRequest;
import com.hsmart.backend.application.dto.ProductRequestDTO;
import com.hsmart.backend.application.dto.ProductResponseDTO;
import com.hsmart.backend.application.dto.ProductStatsResponseDTO;
import com.hsmart.backend.domain.entities.ProductStatus;
import com.hsmart.backend.service.ProductImageAnalysisService;
import com.hsmart.backend.service.ProductListingSuggestionService;
import com.hsmart.backend.service.ProductService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private static final String PRODUCT_CREATED_MESSAGE = "Product created successfully";
    private static final String PRODUCT_CREATED_PENDING_REVIEW_MESSAGE =
            "Product submitted successfully and is pending moderation review.";

    private final ProductService productService;
    private final ProductImageAnalysisService productImageAnalysisService;
    private final ProductListingSuggestionService productListingSuggestionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponseDTO>> createProduct(
            @Valid @ModelAttribute ProductRequestDTO request,
            @RequestPart(name = "files", required = false) List<MultipartFile> files,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "analysisImageIndex", defaultValue = "0") int analysisImageIndex
    ) throws IOException {
        List<MultipartFile> normalizedFiles = normalizeFiles(files, file);
        if (normalizedFiles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }

        ProductResponseDTO response = productService.createProduct(request, normalizedFiles, analysisImageIndex);
        String message = response.getStatus() == ProductStatus.PENDING_REVIEW
                ? PRODUCT_CREATED_PENDING_REVIEW_MESSAGE
                : PRODUCT_CREATED_MESSAGE;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, message, response));
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> files, MultipartFile singleFile) {
        List<MultipartFile> normalizedFiles = new ArrayList<>();
        if (files != null) {
            normalizedFiles.addAll(
                    files.stream()
                            .filter(file -> file != null && !file.isEmpty())
                            .toList()
            );
        }
        if (singleFile != null && !singleFile.isEmpty()) {
            normalizedFiles.add(singleFile);
        }
        return normalizedFiles;
    }

    @PostMapping(value = "/analyze-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageAnalysisResponseDTO>> analyzeImage(
            @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }

        ImageAnalysisResponseDTO response = productImageAnalysisService.analyzeImage(file);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Product image analyzed successfully", response));
    }

    @PostMapping(value = "/prepare-listing", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductListingSuggestionResponseDTO>> prepareListing(
            @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }

        ProductListingSuggestionResponseDTO response = productListingSuggestionService.prepareListing(file);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Product listing suggestions prepared successfully", response));
    }

    @PutMapping(value = "/{id}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<ApiResponse<ProductResponseDTO>> updateProduct(
            @PathVariable Long id,
            @Valid @ModelAttribute ProductRequestDTO request,
            @RequestPart(name = "files", required = false) List<MultipartFile> files,
            @RequestPart(name = "file", required = false) MultipartFile file
    ) throws IOException {
        List<MultipartFile> newImages = normalizeFiles(files, file);
        ProductResponseDTO response = productService.updateProduct(id, request, newImages.isEmpty() ? null : newImages);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Product updated successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Product deleted successfully", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponseDTO<ProductResponseDTO>>> getAllProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long categoryId,
            @ParameterObject
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK, "Products fetched successfully",
                        productService.getAllProducts(keyword, status, categoryId, pageable))
        );
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<Void>> toggleProductLike(@PathVariable Long id) {
        String message = productService.toggleProductLike(id);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, message, null));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponseDTO<ProductResponseDTO>>> getMyProducts(
            @ParameterObject
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK, "My products fetched successfully", productService.getMyProducts(pageable))
        );
    }

    @GetMapping("/wishlist")
    public ResponseEntity<ApiResponse<PageResponseDTO<ProductResponseDTO>>> getWishlist(
            @ParameterObject
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK, "Wishlist fetched successfully", productService.getWishlist(pageable))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK, "Product fetched successfully", productService.getProductById(id))
        );
    }

    @GetMapping("/internal/{id}")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> getProductByIdForAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(HttpStatus.OK, "Product fetched successfully", productService.getProductByIdForAdmin(id))
        );
    }

    @PutMapping("/internal/{id}/moderation-status")
    public ResponseEntity<ApiResponse<ProductResponseDTO>> updateModerationStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProductModerationStatusRequest request
    ) {
        ProductResponseDTO response = productService.updateModerationStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Product moderation status updated successfully", response));
    }

    @GetMapping("/internal/stats")
    public ResponseEntity<ApiResponse<ProductStatsResponseDTO>> getInternalStats() {
        ProductStatsResponseDTO response = productService.getProductStats();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Product stats fetched successfully", response));
    }
}
