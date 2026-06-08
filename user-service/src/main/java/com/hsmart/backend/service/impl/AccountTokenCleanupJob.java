package com.hsmart.backend.service.impl;

import com.hsmart.backend.infrastructure.persistence.AccountTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountTokenCleanupJob {

    private final AccountTokenRepository tokenRepository;

    @Scheduled(cron = "${account.lifecycle.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void deleteExpiredTokens() {
        long deletedCount = tokenRepository.deleteByExpiresAtBefore(Instant.now());
        if (deletedCount > 0) {
            log.info("Deleted {} expired account tokens", deletedCount);
        }
    }
}
