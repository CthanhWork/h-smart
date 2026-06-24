package com.hsmart.backend.application.mapper;

import com.hsmart.backend.application.dto.DetectionDTO;
import com.hsmart.backend.application.dto.ProductResponseDTO;
import com.hsmart.backend.domain.entities.Category;
import com.hsmart.backend.domain.entities.Product;
import com.hsmart.backend.domain.entities.ProductStatus;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.util.StringUtils;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "resolvedTitle")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "negotiable", source = "negotiable")
    @Mapping(target = "minPrice", source = "minPrice")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "sellerId", source = "sellerId")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "imageUrl", source = "relativeImageUrl")
    @Mapping(target = "imageUrls", source = "imageUrlsJson")
    @Mapping(target = "aiMetadata", source = "aiMetadataJson")
    @Mapping(target = "titleModifiedByUser", source = "titleModifiedByUser")
    @Mapping(target = "likeCount", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(
            String description,
            java.math.BigDecimal price,
            boolean negotiable,
            java.math.BigDecimal minPrice,
            ProductStatus status,
            String sellerId,
            Category category,
            String resolvedTitle,
            String relativeImageUrl,
            String imageUrlsJson,
            String aiMetadataJson,
            boolean titleModifiedByUser
    );

    @Mapping(target = "title", source = "product.title")
    @Mapping(target = "status", source = "product.status")
    @Mapping(target = "sellerId", source = "product.sellerId")
    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "product.category.name")
    @Mapping(target = "imageUrl", expression = "java(toAbsoluteImageUrl(publicBaseUrl, product.getImageUrl()))")
    @Mapping(target = "imageUrls", ignore = true)
    @Mapping(target = "aiMetadata", ignore = true)
    @Mapping(target = "numDetections", ignore = true)
    @Mapping(target = "createdAt", source = "product.createdAt")
    @Mapping(target = "titleModifiedByUser", source = "product.titleModifiedByUser")
    ProductResponseDTO toResponse(Product product, String publicBaseUrl);

    default ProductResponseDTO toResponse(Product product, List<DetectionDTO> detections, String publicBaseUrl) {
        ProductResponseDTO response = toResponse(product, publicBaseUrl);
        response.setAiMetadata(detections);
        response.setNumDetections(detections.size());
        return response;
    }

    default String toAbsoluteImageUrl(String publicBaseUrl, String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return imageUrl;
        }

        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }

        if (!StringUtils.hasText(publicBaseUrl)) {
            return imageUrl;
        }

        String normalizedBase = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        String normalizedPath = imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl;
        return normalizedBase + normalizedPath;
    }
}
