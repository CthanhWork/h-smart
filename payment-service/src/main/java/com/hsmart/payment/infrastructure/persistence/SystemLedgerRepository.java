package com.hsmart.payment.infrastructure.persistence;

import com.hsmart.payment.domain.entities.LedgerDirection;
import com.hsmart.payment.domain.entities.LedgerEntryType;
import com.hsmart.payment.domain.entities.SystemLedgerEntry;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemLedgerRepository extends JpaRepository<SystemLedgerEntry, Long> {

    boolean existsByEntryTypeAndReferenceTypeAndReferenceId(
            LedgerEntryType entryType, String referenceType, Long referenceId);

    @Query("select coalesce(sum(e.amount), 0) from SystemLedgerEntry e where e.direction = :direction")
    BigDecimal sumAmountByDirection(@Param("direction") LedgerDirection direction);
}
