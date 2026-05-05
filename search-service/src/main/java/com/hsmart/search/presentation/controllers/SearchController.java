package com.hsmart.search.presentation.controllers;

import com.hsmart.search.application.dto.ApiResponse;
import com.hsmart.search.application.dto.PageResponseDTO;
import com.hsmart.search.application.dto.ProductSearchResponseDTO;
import com.hsmart.search.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class SearchController {

    private final ProductSearchService productSearchService;

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponseDTO<ProductSearchResponseDTO>>> searchProducts(
            @RequestParam(name = "q", required = false) String query,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponseDTO<ProductSearchResponseDTO> products = productSearchService.searchProducts(query, pageable);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Products searched successfully", products));
    }
}
