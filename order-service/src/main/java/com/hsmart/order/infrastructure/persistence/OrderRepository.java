package com.hsmart.order.infrastructure.persistence;

import com.hsmart.order.domain.entities.Order;
import com.hsmart.order.domain.entities.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    long countByStatus(OrderStatus status);

    Optional<Order> findFirstByBuyerIdOrderByCreatedAtDescIdDesc(String buyerId);

    Optional<Order> findByTrackingCode(String trackingCode);

    boolean existsByProductIdAndStatusIn(Long productId, Collection<OrderStatus> statuses);

    Optional<Order> findByIdAndBuyerIdOrIdAndSellerId(Long buyerOrderId, String buyerId, Long sellerOrderId, String sellerId);

    List<Order> findByBuyerIdOrSellerIdOrderByCreatedAtDescIdDesc(String buyerId, String sellerId);

    @Modifying
    @Query("update Order o set o.status = com.hsmart.order.domain.entities.OrderStatus.CANCELLED "
            + "where o.status = com.hsmart.order.domain.entities.OrderStatus.PENDING and o.createdAt < :expiredBefore")
    int cancelExpiredPendingOrders(@Param("expiredBefore") LocalDateTime expiredBefore);

    @Query("select coalesce(sum(o.amount), 0) from Order o where o.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") OrderStatus status);
}
