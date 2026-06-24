package com.hsmart.payment.service.impl;

import com.hsmart.payment.application.dto.CreateDepositRequestDTO;
import com.hsmart.payment.application.dto.CreateOrderInternalRequestDTO;
import com.hsmart.payment.application.dto.DepositResponseDTO;
import com.hsmart.payment.application.dto.OrderResponseDTO;
import com.hsmart.payment.application.dto.ShippingEstimateResponseDTO;
import com.hsmart.payment.application.exceptions.InvalidPaymentCallbackException;
import com.hsmart.payment.application.exceptions.PaymentNotFoundException;
import com.hsmart.payment.application.exceptions.PaymentStateException;
import com.hsmart.payment.domain.entities.DeliveryMethod;
import com.hsmart.payment.domain.entities.DepositPayment;
import com.hsmart.payment.domain.entities.PaymentStatus;
import com.hsmart.payment.infrastructure.config.VnpayProperties;
import com.hsmart.payment.infrastructure.persistence.DepositPaymentRepository;
import com.hsmart.payment.infrastructure.vnpay.VnpayService;
import com.hsmart.payment.service.OrderClient;
import com.hsmart.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final DepositPaymentRepository depositPaymentRepository;
    private final OrderClient orderClient;
    private final VnpayService vnpayService;
    private final VnpayProperties vnpayProperties;

    @Value("${payments.pending-timeout-minutes:30}")
    private long pendingTimeoutMinutes;

    @Override
    public DepositResponseDTO createDeposit(CreateDepositRequestDTO request, String buyerId, String clientIp) {
        DeliveryMethod deliveryMethod = request.getDeliveryMethod() != null
                ? request.getDeliveryMethod()
                : DeliveryMethod.VIETTEL_POST;

        if (depositPaymentRepository.existsByProductIdAndBuyerIdAndStatus(
                request.getProductId(), buyerId, PaymentStatus.PENDING)) {
            throw new PaymentStateException("You already have a pending deposit for this product");
        }

        // Deposit amount == shipping fee. order-service also validates the product is orderable.
        ShippingEstimateResponseDTO estimate =
                orderClient.getShippingEstimate(request.getProductId(), deliveryMethod, buyerId);
        BigDecimal amount = estimate.shippingFee();
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentStateException("Shipping fee is unavailable for this product");
        }

        DepositPayment payment = DepositPayment.builder()
                .txnRef(generateTxnRef())
                .buyerId(buyerId)
                .productId(request.getProductId())
                .offerId(request.getOfferId())
                .deliveryMethod(deliveryMethod)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .build();

        DepositPayment saved = depositPaymentRepository.save(payment);
        String paymentUrl = vnpayService.buildPaymentUrl(saved, clientIp);
        log.info("Created deposit {} (txnRef {}) for buyer {} product {} amount {}",
                saved.getId(), saved.getTxnRef(), buyerId, saved.getProductId(), amount);

        DepositResponseDTO response = toResponse(saved);
        response.setPaymentUrl(paymentUrl);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public DepositResponseDTO getDeposit(Long id, String buyerId) {
        DepositPayment payment = depositPaymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Deposit payment was not found"));
        if (!payment.getBuyerId().equals(buyerId)) {
            throw new PaymentStateException("Deposit does not belong to the current user");
        }
        return toResponse(payment);
    }

    @Override
    public String handleReturn(Map<String, String> params) {
        ProcessOutcome outcome = processCallback(params);
        return vnpayProperties.resultRedirectUrl()
                + "?status=" + outcome.status()
                + "&txnRef=" + nullSafe(params.get("vnp_TxnRef"))
                + (outcome.orderId() != null ? "&orderId=" + outcome.orderId() : "");
    }

    @Override
    public Map<String, String> handleIpn(Map<String, String> params) {
        if (!vnpayService.isValidSignature(params)) {
            return Map.of("RspCode", "97", "Message", "Invalid checksum");
        }
        DepositPayment payment = depositPaymentRepository.findByTxnRef(params.get("vnp_TxnRef")).orElse(null);
        if (payment == null) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        }
        if (!amountMatches(payment, params)) {
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }

        applyCallback(payment, params);
        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    @Override
    public void settleByOrderId(Long orderId) {
        markByOrderId(orderId, PaymentStatus.SETTLED);
    }

    @Override
    public void refundByOrderId(Long orderId) {
        markByOrderId(orderId, PaymentStatus.REFUNDED);
    }

    private void markByOrderId(Long orderId, PaymentStatus target) {
        DepositPayment payment = depositPaymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            log.info("No deposit found for order {}; skipping {} marking", orderId, target);
            return;
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            log.info("Deposit {} for order {} is {}, not PAID; skipping {} marking",
                    payment.getId(), orderId, payment.getStatus(), target);
            return;
        }
        payment.setStatus(target);
        depositPaymentRepository.save(payment);
        log.info("Marked deposit {} for order {} as {}", payment.getId(), orderId, target);
    }

    /** Shared processing for the Return callback (verifies and applies the result). */
    private ProcessOutcome processCallback(Map<String, String> params) {
        if (!vnpayService.isValidSignature(params)) {
            throw new InvalidPaymentCallbackException("Invalid VNPay checksum");
        }
        DepositPayment payment = depositPaymentRepository.findByTxnRef(params.get("vnp_TxnRef"))
                .orElseThrow(() -> new PaymentNotFoundException("Deposit payment was not found for the transaction"));

        if (payment.getStatus() == PaymentStatus.PAID
                || payment.getStatus() == PaymentStatus.SETTLED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            // Already processed (e.g. IPN arrived first) — idempotent success.
            return new ProcessOutcome("success", payment.getOrderId());
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return new ProcessOutcome("failed", null);
        }
        if (!amountMatches(payment, params)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setVnpResponseCode(params.get("vnp_ResponseCode"));
            depositPaymentRepository.save(payment);
            return new ProcessOutcome("failed", null);
        }

        Long orderId = applyCallback(payment, params);
        return new ProcessOutcome(orderId != null ? "success" : "failed", orderId);
    }

    /** Applies a verified callback to a PENDING payment; on success creates the order. Returns orderId or null. */
    private Long applyCallback(DepositPayment payment, Map<String, String> params) {
        payment.setVnpResponseCode(params.get("vnp_ResponseCode"));
        payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        payment.setVnpBankCode(params.get("vnp_BankCode"));

        if (!vnpayService.isSuccessfulResponse(params)) {
            payment.setStatus(PaymentStatus.FAILED);
            depositPaymentRepository.save(payment);
            log.info("Deposit {} (txnRef {}) failed with response code {}",
                    payment.getId(), payment.getTxnRef(), params.get("vnp_ResponseCode"));
            return null;
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());

        try {
            OrderResponseDTO order = orderClient.createOrderFromDeposit(
                    CreateOrderInternalRequestDTO.builder()
                            .productId(payment.getProductId())
                            .deliveryMethod(payment.getDeliveryMethod())
                            .offerId(payment.getOfferId())
                            .depositPaymentId(payment.getId())
                            .build(),
                    payment.getBuyerId()
            );
            payment.setOrderId(order.id());
            log.info("Deposit {} paid; created order {} for buyer {}",
                    payment.getId(), order.id(), payment.getBuyerId());
        } catch (RuntimeException exception) {
            // Payment succeeded but the order could not be created (e.g. product taken meanwhile).
            // Keep the payment PAID with no order; this requires a manual refund.
            log.error("Deposit {} (txnRef {}) was paid but order creation failed — manual refund required: {}",
                    payment.getId(), payment.getTxnRef(), exception.getMessage(), exception);
        }

        depositPaymentRepository.save(payment);
        return payment.getOrderId();
    }

    @Scheduled(fixedDelayString = "${payments.pending-timeout-scan-ms:60000}")
    public void expirePendingDeposits() {
        if (pendingTimeoutMinutes <= 0) {
            return;
        }
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        List<DepositPayment> expired =
                depositPaymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, expiredBefore);
        if (expired.isEmpty()) {
            return;
        }
        expired.forEach(payment -> payment.setStatus(PaymentStatus.EXPIRED));
        depositPaymentRepository.saveAll(expired);
        log.info("Expired {} pending deposit payments", expired.size());
    }

    private boolean amountMatches(DepositPayment payment, Map<String, String> params) {
        String vnpAmount = params.get("vnp_Amount");
        if (vnpAmount == null) {
            return false;
        }
        try {
            long expected = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
            return expected == Long.parseLong(vnpAmount.trim());
        } catch (NumberFormatException | ArithmeticException exception) {
            return false;
        }
    }

    private String generateTxnRef() {
        return "HS" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private DepositResponseDTO toResponse(DepositPayment payment) {
        return DepositResponseDTO.builder()
                .id(payment.getId())
                .txnRef(payment.getTxnRef())
                .productId(payment.getProductId())
                .offerId(payment.getOfferId())
                .deliveryMethod(payment.getDeliveryMethod())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .orderId(payment.getOrderId())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private record ProcessOutcome(String status, Long orderId) {
    }
}
