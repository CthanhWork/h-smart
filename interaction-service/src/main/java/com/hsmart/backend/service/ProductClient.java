package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.ProductCatalogItem;
import java.util.List;

public interface ProductClient {
    List<ProductCatalogItem> findRelevantProducts(List<String> keywords, String userId);
}
