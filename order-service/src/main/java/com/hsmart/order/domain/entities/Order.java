package com.hsmart.order.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buyer_id", nullable = false, length = 150)
    private String buyerId;

    @Column(name = "seller_id", nullable = false, length = 150)
    private String sellerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "product_amount", nullable = false, precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    private BigDecimal productAmount;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    private BigDecimal shippingFee;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 30, columnDefinition = "varchar(30) default 'VIETTEL_POST'")
    private DeliveryMethod deliveryMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = OrderStatus.PENDING;
        }
        if (shippingFee == null) {
            shippingFee = BigDecimal.ZERO;
        }
        if (productAmount == null && amount != null) {
            productAmount = amount.subtract(shippingFee);
        }
        if (deliveryMethod == null) {
            deliveryMethod = DeliveryMethod.VIETTEL_POST;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
