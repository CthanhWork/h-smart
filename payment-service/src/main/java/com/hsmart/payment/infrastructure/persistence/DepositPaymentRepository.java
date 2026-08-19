package com.hsmart.payment.infrastructure.persistence;

import com.hsmart.payment.domain.entities.DepositPayment;
import com.hsmart.payment.domain.entities.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositPaymentRepository extends JpaRepository<DepositPayment, Long> {

    Optional<DepositPayment> findByTxnRef(String txnRef);

    Optional<DepositPayment> findByOrderId(Long orderId);

    Page<DepositPayment> findByBuyerIdOrderByCreatedAtDescIdDesc(String buyerId, Pageable pageable);

    boolean existsByProductIdAndBuyerIdAndStatus(Long productId, String buyerId, PaymentStatus status);

    List<DepositPayment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime createdBefore);

    List<DepositPayment> findByStatusAndOrderIdIsNullAndPaidAtBefore(PaymentStatus status, LocalDateTime paidBefore);
}
