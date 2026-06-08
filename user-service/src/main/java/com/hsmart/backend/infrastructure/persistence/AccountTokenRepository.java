package com.hsmart.backend.infrastructure.persistence;

import com.hsmart.backend.domain.entities.AccountToken;
import com.hsmart.backend.domain.entities.AccountTokenType;
import com.hsmart.backend.domain.entities.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    Optional<AccountToken> findByTokenHashAndType(String tokenHash, AccountTokenType type);
    void deleteAllByUserAndType(User user, AccountTokenType type);
    long deleteByExpiresAtBefore(Instant cutoff);
}
