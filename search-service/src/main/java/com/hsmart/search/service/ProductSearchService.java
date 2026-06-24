package com.hsmart.search.service;

import com.hsmart.search.application.dto.PageResponseDTO;
import com.hsmart.search.application.dto.ProductSearchEvent;
import com.hsmart.search.application.dto.ProductSearchResponseDTO;
import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;

public interface ProductSearchService {
    PageResponseDTO<ProductSearchResponseDTO> searchProducts(
            String query,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    );

    void indexProduct(ProductSearchEvent event);

    void deleteProduct(Long id);
}
