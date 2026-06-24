package com.hsmart.order.service;

public interface PaymentClient {

    /** Best-effort: mark the deposit tied to a completed order as settled. */
    void settleDeposit(Long orderId);

    /** Best-effort: mark the deposit tied to a cancelled order as refunded. */
    void refundDeposit(Long orderId);
}
