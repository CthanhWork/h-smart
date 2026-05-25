package com.hsmart.backend.infrastructure.persistence;

import com.hsmart.backend.application.dto.AveragePriceByLabelProjection;
import com.hsmart.backend.domain.entities.Product;
import com.hsmart.backend.domain.entities.ProductStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findAllByIsDeletedFalse(Pageable pageable);

    @Query("""
            select product
            from Product product
            left join product.category category
            where product.isDeleted = false
              and (:status is null or product.status = :status)
              and (:categoryId is null or category.id = :categoryId)
              and (
                    :keyword is null
                    or lower(product.title) like lower(concat('%', :keyword, '%'))
                    or lower(product.description) like lower(concat('%', :keyword, '%'))
                    or lower(category.name) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("status") ProductStatus status,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );

    Optional<Product> findByIdAndIsDeletedFalse(Long id);

    long countByIsDeletedFalseAndStatusIn(Collection<ProductStatus> statuses);

    @Query(value = """
            select detection.value ->> 'label' as label,
                   avg(product.price) as "averagePrice"
            from products product
            cross join lateral jsonb_array_elements(product.ai_metadata) as detection(value)
            where product.is_deleted = false
              and product.status = 'SOLD'
              and detection.value ->> 'label' is not null
              and detection.value ->> 'label' <> ''
            group by detection.value ->> 'label'
            """, nativeQuery = true)
    List<AveragePriceByLabelProjection> findAverageSoldPricesByAiLabel();
}
