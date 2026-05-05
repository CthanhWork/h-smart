package com.hsmart.order.infrastructure.persistence;

import com.hsmart.order.domain.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
