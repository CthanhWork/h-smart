package com.hsmart.order.infrastructure.persistence;

import com.hsmart.order.domain.entities.Order;
import com.hsmart.order.domain.entities.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<Order> findByBuyerIdOrSellerIdOrderByCreatedAtDescIdDesc(String buyerId, String sellerId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = com.hsmart.order.domain.entities.OrderStatus.PENDING AND o.createdAt < :expiredBefore")
    List<Order> findPendingOrdersExpiredBefore(@Param("expiredBefore") LocalDateTime expiredBefore);

    @Modifying
    @Query("update Order o set o.status = com.hsmart.order.domain.entities.OrderStatus.CANCELLED "
            + "where o.status = com.hsmart.order.domain.entities.OrderStatus.PENDING and o.createdAt < :expiredBefore")
    int cancelExpiredPendingOrders(@Param("expiredBefore") LocalDateTime expiredBefore);

    @Query("select coalesce(sum(o.amount), 0) from Order o where o.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") OrderStatus status);

    @Query("SELECT o FROM Order o WHERE (:status IS NULL OR o.status = :status) ORDER BY o.createdAt DESC")
    Page<Order> findAllForAdmin(@Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = com.hsmart.order.domain.entities.OrderStatus.RETURN_REQUESTED " +
           "AND o.returnSellerApproved = false AND o.returnRequestedAt < :timeoutBefore")
    List<Order> findReturnRequestsPendingSellerApproval(@Param("timeoutBefore") LocalDateTime timeoutBefore);
}
