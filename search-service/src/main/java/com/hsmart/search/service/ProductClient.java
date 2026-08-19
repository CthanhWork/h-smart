package com.hsmart.search.service;

import com.hsmart.search.application.dto.ProductSearchEvent;
import java.util.List;

public interface ProductClient {
    List<ProductSearchEvent> getAllVisibleProducts();
}
