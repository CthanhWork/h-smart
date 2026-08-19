package com.hsmart.payment.infrastructure.persistence;

import com.hsmart.payment.domain.entities.SystemAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemAccountRepository extends JpaRepository<SystemAccount, Long> {

    Optional<SystemAccount> findByCode(String code);
}
