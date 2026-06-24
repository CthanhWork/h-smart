package com.hsmart.payment.presentation.controllers;

import com.hsmart.payment.application.dto.ApiResponse;
import com.hsmart.payment.application.dto.CreateDepositRequestDTO;
import com.hsmart.payment.application.dto.DepositResponseDTO;
import com.hsmart.payment.application.exceptions.MissingUserContextException;
import com.hsmart.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<DepositResponseDTO>> createDeposit(
            @Valid @RequestBody CreateDepositRequestDTO request,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            HttpServletRequest httpRequest
    ) {
        DepositResponseDTO response = paymentService.createDeposit(request, requireUserId(buyerId), resolveClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Deposit payment created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepositResponseDTO>> getDeposit(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String buyerId
    ) {
        DepositResponseDTO response = paymentService.getDeposit(id, requireUserId(buyerId));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Deposit payment fetched successfully", response));
    }

    /** Browser redirect back from VNPay; verifies and redirects to the frontend result page. */
    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> handleReturn(@RequestParam Map<String, String> params) {
        String redirectUrl = paymentService.handleReturn(params);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    /** Server-to-server IPN from VNPay; must answer with the VNPay-expected JSON body. */
    @GetMapping("/vnpay/ipn")
    public ResponseEntity<Map<String, String>> handleIpn(@RequestParam Map<String, String> params) {
        log.info("Received VNPay IPN for txnRef {}", params.get("vnp_TxnRef"));
        return ResponseEntity.ok(paymentService.handleIpn(params));
    }

    @PostMapping("/internal/{orderId}/settle")
    public ResponseEntity<ApiResponse<Void>> settle(@PathVariable Long orderId) {
        paymentService.settleByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Deposit settled", null));
    }

    @PostMapping("/internal/{orderId}/refund")
    public ResponseEntity<ApiResponse<Void>> refund(@PathVariable Long orderId) {
        paymentService.refundByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Deposit refunded", null));
    }

    private String requireUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new MissingUserContextException();
        }
        return userId.trim();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
