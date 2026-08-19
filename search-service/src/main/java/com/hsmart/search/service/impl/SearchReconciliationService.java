package com.hsmart.search.service.impl;

import com.hsmart.search.application.dto.ProductSearchEvent;
import com.hsmart.search.infrastructure.config.SearchReconciliationProperties;
import com.hsmart.search.service.ProductClient;
import com.hsmart.search.service.ProductSearchService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "search.reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SearchReconciliationService {

    private final ProductSearchService productSearchService;
    private final ProductClient productClient;
    private final SearchReconciliationProperties reconciliationProperties;

    /**
     * Nightly job to reconcile Elasticsearch index with product-service database.
     * Fixes out-of-sync issues caused by missed events or service downtime.
     */
    @Scheduled(cron = "${search.reconciliation.cron:0 0 2 * * *}")
    public void reconcileSearchIndex() {
        if (!reconciliationProperties.enabled()) {
            log.debug("Search reconciliation is disabled");
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("Starting search index reconciliation");

        try {
            // 1. Fetch all visible (APPROVED/ACTIVE) products from product-service
            List<ProductSearchEvent> visibleProducts = productClient.getAllVisibleProducts();
            Set<Long> dbProductIds = visibleProducts.stream()
                    .map(ProductSearchEvent::getId)
                    .collect(Collectors.toSet());
            log.info("Fetched {} visible products from product-service", dbProductIds.size());

            // 2. Fetch all indexed product IDs from Elasticsearch
            Set<Long> indexedProductIds = productSearchService.getAllIndexedProductIds();
            log.info("Found {} products in Elasticsearch index", indexedProductIds.size());

            // 3. Find missing products (in DB but not in ES)
            Set<Long> missingInIndex = new HashSet<>(dbProductIds);
            missingInIndex.removeAll(indexedProductIds);

            // 4. Reindex missing products
            int reindexed = 0;
            for (Long productId : missingInIndex) {
                ProductSearchEvent product = visibleProducts.stream()
                        .filter(p -> p.getId().equals(productId))
                        .findFirst()
                        .orElse(null);

                if (product != null) {
                    try {
                        productSearchService.indexProduct(product);
                        reindexed++;
                    } catch (Exception ex) {
                        log.error("Failed to reindex product {}: {}", productId, ex.getMessage());
                    }
                }
            }

            // 5. Find orphaned products (in ES but not in DB or not visible)
            Set<Long> orphanedInIndex = new HashSet<>(indexedProductIds);
            orphanedInIndex.removeAll(dbProductIds);

            // 6. Delete orphaned products
            int deleted = 0;
            for (Long productId : orphanedInIndex) {
                try {
                    productSearchService.deleteProduct(productId);
                    deleted++;
                } catch (Exception ex) {
                    log.error("Failed to delete orphaned product {}: {}", productId, ex.getMessage());
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Search index reconciliation completed in {} ms: {} products reindexed,  orphaned products deleted",
                    durationMs, reindexed, deleted);

        } catch (Exception ex) {
            log.error("Search index reconciliation failed: {}", ex.getMessage(), ex);
        }
    }
}
