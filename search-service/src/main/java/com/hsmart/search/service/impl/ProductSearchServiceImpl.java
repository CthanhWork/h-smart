package com.hsmart.search.service.impl;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import com.hsmart.search.application.dto.PageResponseDTO;
import com.hsmart.search.application.dto.ProductSearchEvent;
import com.hsmart.search.application.dto.ProductSearchResponseDTO;
import com.hsmart.search.domain.ProductDocument;
import com.hsmart.search.infrastructure.persistence.ProductSearchRepository;
import com.hsmart.search.service.ProductSearchService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ProductSearchServiceImpl implements ProductSearchService {

    private final ProductSearchRepository productSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final List<FieldValue> visibleStatusValues;

    public ProductSearchServiceImpl(
            ProductSearchRepository productSearchRepository,
            ElasticsearchOperations elasticsearchOperations,
            @Value("${search.visible-statuses:APPROVED,ACTIVE}") List<String> visibleStatuses
    ) {
        this.productSearchRepository = productSearchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.visibleStatusValues = visibleStatuses.stream()
                .filter(StringUtils::hasText)
                .map(status -> FieldValue.of(status.trim()))
                .toList();
    }

    @Override
    public PageResponseDTO<ProductSearchResponseDTO> searchProducts(
            String query,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String provinceCode,
            Pageable pageable
    ) {
        long startedAt = System.nanoTime();

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(buildSearchQuery(query, category, minPrice, maxPrice, provinceCode))
                .withPageable(pageable)
                .build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(searchQuery, ProductDocument.class);
        List<ProductSearchResponseDTO> content = searchHits.stream()
                .map(SearchHit::getContent)
                .map(this::toResponse)
                .toList();

        log.info("Executed Elasticsearch product search in {} ms with {} hits (query='{}', page={}, size={})",
                elapsedMillis(startedAt), searchHits.getTotalHits(), query, pageable.getPageNumber(), pageable.getPageSize());
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
                .provinceCode(normalize(event.getProvinceCode()))
                .province(normalize(event.getProvince()))
                .imageUrl(normalize(event.getImageUrl()))
                .build();

        productSearchRepository.save(document);
        log.info("Indexed product {} into Elasticsearch products_index", document.getId());
    }

    @Override
    public void deleteProduct(Long id) {
        if (id == null) {
            log.warn("Ignored product delete event with null id");
            return;
        }
        productSearchRepository.deleteById(id);
        log.info("Removed product {} from Elasticsearch products_index", id);
    }

    @Override
    public Set<Long> getAllIndexedProductIds() {
        Iterable<ProductDocument> allProducts = productSearchRepository.findAll();
        return StreamSupport.stream(allProducts.spliterator(), false)
                .map(ProductDocument::getId)
                .collect(Collectors.toSet());
    }

    private Query buildSearchQuery(String query, String category, BigDecimal minPrice, BigDecimal maxPrice, String provinceCode) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (StringUtils.hasText(query)) {
            String searchText = query.trim();
            bool.must(must -> must.multiMatch(multiMatch -> multiMatch
                    .query(searchText)
                    .fields("title^3", "categoryName^2", "description")
                    .fuzziness("AUTO")));
        } else {
            bool.must(must -> must.matchAll(matchAll -> matchAll));
        }

        if (!visibleStatusValues.isEmpty()) {
            bool.filter(filter -> filter.terms(terms -> terms
                    .field("status")
                    .terms(values -> values.value(visibleStatusValues))));
        }

        if (StringUtils.hasText(category)) {
            String categoryText = category.trim();
            bool.filter(filter -> filter.match(match -> match
                    .field("categoryName")
                    .query(categoryText)));
        }

        if (StringUtils.hasText(provinceCode)) {
            String provinceCodeText = provinceCode.trim();
            bool.filter(filter -> filter.term(term -> term
                    .field("provinceCode")
                    .value(provinceCodeText)));
        }

        if (minPrice != null || maxPrice != null) {
            bool.filter(filter -> filter.range(range -> {
                range.field("price");
                if (minPrice != null) {
                    range.gte(JsonData.of(minPrice));
                }
                if (maxPrice != null) {
                    range.lte(JsonData.of(maxPrice));
                }
                return range;
            }));
        }

        return Query.of(builder -> builder.bool(bool.build()));
    }

    private ProductSearchResponseDTO toResponse(ProductDocument document) {
        return ProductSearchResponseDTO.builder()
                .id(document.getId())
                .title(document.getTitle())
                .description(document.getDescription())
                .price(document.getPrice())
                .categoryName(document.getCategoryName())
                .status(document.getStatus())
                .provinceCode(document.getProvinceCode())
                .province(document.getProvince())
                .imageUrl(document.getImageUrl())
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
