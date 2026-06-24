package com.hsmart.payment.service;

import com.hsmart.payment.application.dto.CreateDepositRequestDTO;
import com.hsmart.payment.application.dto.DepositResponseDTO;
import java.util.Map;

public interface PaymentService {

    /** Creates a PENDING deposit (== shipping fee) and returns a VNPay payment URL. */
    DepositResponseDTO createDeposit(CreateDepositRequestDTO request, String buyerId, String clientIp);

    DepositResponseDTO getDeposit(Long id, String buyerId);

    /** Handles the browser Return URL callback; returns the FE redirect URL. */
    String handleReturn(Map<String, String> params);

    /** Handles the server-to-server IPN callback; returns the VNPay-expected response body. */
    Map<String, String> handleIpn(Map<String, String> params);

    /** Marks the deposit tied to an order as SETTLED (order completed). */
    void settleByOrderId(Long orderId);

    /** Marks the deposit tied to an order as REFUNDED (order cancelled). */
    void refundByOrderId(Long orderId);
}
