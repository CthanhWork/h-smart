package com.hsmart.payment.domain.entities;

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

/**
 * VNPay payment made by a seller for the platform fee of an order (paid at order confirmation).
 * Mirrors {@link DepositPayment} but the money is credited to the {@link SystemAccount}
 * instead of being held against the order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "platform_fee_payments")
public class PlatformFeePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique VNPay transaction reference (vnp_TxnRef). */
    @Column(name = "txn_ref", nullable = false, unique = true, length = 64)
    private String txnRef;

    @Column(name = "seller_id", nullable = false, length = 150)
    private String sellerId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "product_id")
    private Long productId;

    /** Platform fee amount in VND = min(shippingFee, productAmount * maxRate). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "vnp_transaction_no", length = 64)
    private String vnpTransactionNo;

    @Column(name = "vnp_response_code", length = 10)
    private String vnpResponseCode;

    @Column(name = "vnp_bank_code", length = 30)
    private String vnpBankCode;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

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
            status = PaymentStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
