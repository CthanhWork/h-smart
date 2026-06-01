package com.hsmart.order.infrastructure.persistence;

import com.hsmart.order.domain.entities.Order;
import com.hsmart.order.domain.entities.OrderStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    long countByStatus(OrderStatus status);

    Optional<Order> findFirstByBuyerIdOrderByCreatedAtDescIdDesc(String buyerId);

    Optional<Order> findByTrackingCode(String trackingCode);

    @Query("select coalesce(sum(o.amount), 0) from Order o where o.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") OrderStatus status);
}
