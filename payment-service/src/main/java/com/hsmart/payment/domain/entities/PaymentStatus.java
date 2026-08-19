package com.hsmart.payment.domain.entities;

public enum PaymentStatus {
    /** Deposit created, waiting for the buyer to pay through VNPay. */
    PENDING,
    /** Deposit paid successfully and the order has been created. */
    PAID,
    /** VNPay reported a failed/declined transaction. */
    FAILED,
    /** Buyer never completed the payment within the allowed window. */
    EXPIRED,
    /** Deposit returned to the buyer because the order was cancelled. */
    REFUNDED,
    /** Deposit consumed (applied to shipping fee) because the order completed. */
    SETTLED
}
