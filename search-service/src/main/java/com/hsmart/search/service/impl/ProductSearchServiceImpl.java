package com.hsmart.search.service.impl;

import com.hsmart.search.application.dto.PageResponseDTO;
import com.hsmart.search.application.dto.ProductSearchEvent;
import com.hsmart.search.application.dto.ProductSearchResponseDTO;
import com.hsmart.search.domain.ProductDocument;
import com.hsmart.search.infrastructure.persistence.ProductSearchRepository;
import com.hsmart.search.service.ProductSearchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImpl implements ProductSearchService {

    private final ProductSearchRepository productSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public PageResponseDTO<ProductSearchResponseDTO> searchProducts(String query, Pageable pageable) {
        long startedAt = System.nanoTime();

        if (!StringUtils.hasText(query)) {
            Page<ProductSearchResponseDTO> page = productSearchRepository.findAll(pageable)
                    .map(this::toResponse);
            log.info("Executed Elasticsearch product browse query in {} ms with {} hits",
                    elapsedMillis(startedAt), page.getTotalElements());
            return PageResponseDTO.from(page);
        }

        String searchText = query.trim();
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(builder -> builder.multiMatch(multiMatch -> multiMatch
                        .query(searchText)
                        .fields("title^3", "categoryName^2", "description")
                        .fuzziness("AUTO")
                ))
                .withPageable(pageable)
                .build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(searchQuery, ProductDocument.class);
        List<ProductSearchResponseDTO> content = searchHits.stream()
                .map(SearchHit::getContent)
                .map(this::toResponse)
                .toList();

        log.info("Executed Elasticsearch fuzzy product query in {} ms with {} hits",
                elapsedMillis(startedAt), searchHits.getTotalHits());
        return PageResponseDTO.of(content, pageable, searchHits.getTotalHits());
    }

    @Override
    public void indexProduct(ProductSearchEvent event) {
        if (event == null || event.getId() == null || !StringUtils.hasText(event.getTitle())) {
            log.warn("Ignored invalid product search event");
            return;
        }

        ProductDocument document = ProductDocument.builder()
                .id(event.getId())
                .title(event.getTitle().trim())
                .description(normalize(event.getDescription()))
                .price(event.getPrice())
                .categoryName(normalize(event.getCategoryName()))
                .status(normalize(event.getStatus()))
                .build();

        productSearchRepository.save(document);
        log.info("Indexed product {} into Elasticsearch products_index", document.getId());
    }

    private ProductSearchResponseDTO toResponse(ProductDocument document) {
        return ProductSearchResponseDTO.builder()
                .id(document.getId())
                .title(document.getTitle())
                .description(document.getDescription())
                .price(document.getPrice())
                .categoryName(document.getCategoryName())
                .status(document.getStatus())
                .build();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
