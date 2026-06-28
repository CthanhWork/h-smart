package com.hsmart.payment.infrastructure.persistence;

import com.hsmart.payment.domain.entities.PaymentStatus;
import com.hsmart.payment.domain.entities.PlatformFeePayment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformFeePaymentRepository extends JpaRepository<PlatformFeePayment, Long> {

    Optional<PlatformFeePayment> findByTxnRef(String txnRef);

    Optional<PlatformFeePayment> findByOrderId(Long orderId);

    boolean existsByOrderIdAndStatusIn(Long orderId, List<PaymentStatus> statuses);

    List<PlatformFeePayment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime createdAt);
}
