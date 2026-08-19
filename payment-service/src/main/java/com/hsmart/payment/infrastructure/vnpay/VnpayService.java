package com.hsmart.payment.infrastructure.vnpay;

import com.hsmart.payment.domain.entities.DepositPayment;
import com.hsmart.payment.infrastructure.config.VnpayProperties;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds VNPay (sandbox) payment URLs and verifies the secure hash on the
 * Return URL / IPN callbacks. Follows the VNPay 2.1.0 specification:
 * parameters are sorted ascending, URL-encoded, joined with '&' and signed
 * with HMAC-SHA512 using the merchant hash secret.
 */
@Slf4j
@Component
public class VnpayService {

    private static final String HMAC_SHA512 = "HmacSHA512";
    private static final DateTimeFormatter VNP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String SUCCESS_RESPONSE_CODE = "00";

    private final VnpayProperties properties;

    public VnpayService(VnpayProperties properties) {
        this.properties = properties;
    }

    public String buildPaymentUrl(DepositPayment payment, String clientIp) {
        return buildPaymentUrl(
                payment.getTxnRef(),
                payment.getAmount(),
                "Dat coc van chuyen don hang san pham " + payment.getProductId(),
                clientIp
        );
    }

    /**
     * Builds a VNPay payment URL for an arbitrary transaction. Used by both the buyer deposit
     * flow and the seller platform-fee flow; the {@code vnp_TxnRef} disambiguates them on callback.
     */
    public String buildPaymentUrl(String txnRef, BigDecimal amount, String orderInfo, String clientIp) {
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        long amountInMinorUnit = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", properties.version());
        params.put("vnp_Command", properties.command());
        params.put("vnp_TmnCode", properties.tmnCode());
        params.put("vnp_Amount", Long.toString(amountInMinorUnit));
        params.put("vnp_CurrCode", properties.currencyCode());
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", properties.orderType());
        params.put("vnp_Locale", StringUtils.hasText(properties.locale()) ? properties.locale() : "vn");
        params.put("vnp_ReturnUrl", properties.returnUrl());
        params.put("vnp_IpAddr", StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1");
        params.put("vnp_CreateDate", now.format(VNP_DATE_FORMAT));
        params.put("vnp_ExpireDate", now.plusMinutes(properties.expireMinutes()).format(VNP_DATE_FORMAT));

        String hashData = buildEncodedData(params);
        String query = hashData; // identical encoding for query string and hash data
        String secureHash = hmacSha512(properties.hashSecret(), hashData);

        return properties.payUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    /** Verifies the secure hash on a Return/IPN callback parameter map. */
    public boolean isValidSignature(Map<String, String> callbackParams) {
        String providedHash = callbackParams.get("vnp_SecureHash");
        if (!StringUtils.hasText(providedHash)) {
            return false;
        }

        Map<String, String> signedParams = new TreeMap<>();
        callbackParams.forEach((key, value) -> {
            if (!"vnp_SecureHash".equals(key) && !"vnp_SecureHashType".equals(key) && StringUtils.hasText(value)) {
                signedParams.put(key, value);
            }
        });

        String hashData = buildEncodedData(signedParams);
        String expectedHash = hmacSha512(properties.hashSecret(), hashData);
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                providedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    public boolean isSuccessfulResponse(Map<String, String> callbackParams) {
        return SUCCESS_RESPONSE_CODE.equals(callbackParams.get("vnp_ResponseCode"))
                && SUCCESS_RESPONSE_CODE.equals(callbackParams.getOrDefault("vnp_TransactionStatus", SUCCESS_RESPONSE_CODE));
    }

    private String buildEncodedData(Map<String, String> sortedParams) {
        List<String> pairs = new ArrayList<>(sortedParams.size());
        sortedParams.forEach((key, value) ->
                pairs.add(key + "=" + URLEncoder.encode(value, StandardCharsets.US_ASCII)));
        return String.join("&", pairs);
    }

    private String hmacSha512(String secret, String data) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("VNPAY_HASH_SECRET must be configured for payment-service");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA512);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign VNPay request", exception);
        }
    }
}
