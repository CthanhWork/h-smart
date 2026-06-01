package com.hsmart.backend.infrastructure.persistence;

import com.hsmart.backend.domain.entities.ProductLike;
import com.hsmart.backend.domain.entities.ProductStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductLikeRepository extends JpaRepository<ProductLike, Long> {

    Optional<ProductLike> findByUserIdAndProductId(String userId, Long productId);

    @EntityGraph(attributePaths = {"product", "product.category"})
    Page<ProductLike> findAllByUserIdAndProductIsDeletedFalseAndProductStatusNot(
            String userId,
            ProductStatus status,
            Pageable pageable
    );
}
