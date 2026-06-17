package com.hsmart.order.infrastructure.persistence;

import com.hsmart.order.domain.entities.OfferStatus;
import com.hsmart.order.domain.entities.ProductOffer;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOfferRepository extends JpaRepository<ProductOffer, Long> {

    boolean existsByProductIdAndBuyerIdAndStatusIn(Long productId, String buyerId, Collection<OfferStatus> statuses);

    List<ProductOffer> findByBuyerIdOrSellerIdOrderByCreatedAtDescIdDesc(String buyerId, String sellerId);

    List<ProductOffer> findByProductIdAndStatusIn(Long productId, Collection<OfferStatus> statuses);

    @Modifying
    @Query("update ProductOffer o set o.status = com.hsmart.order.domain.entities.OfferStatus.EXPIRED "
            + "where o.status = com.hsmart.order.domain.entities.OfferStatus.PENDING and o.expiresAt < :now")
    int expirePendingOffers(@Param("now") LocalDateTime now);
}
