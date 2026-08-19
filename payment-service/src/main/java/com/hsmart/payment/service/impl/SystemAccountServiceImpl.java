package com.hsmart.payment.service.impl;

import com.hsmart.payment.application.dto.PageResponseDTO;
import com.hsmart.payment.application.dto.SystemAccountSummaryDTO;
import com.hsmart.payment.application.dto.SystemLedgerEntryDTO;
import com.hsmart.payment.domain.entities.LedgerDirection;
import com.hsmart.payment.domain.entities.LedgerEntryType;
import com.hsmart.payment.domain.entities.SystemAccount;
import com.hsmart.payment.domain.entities.SystemLedgerEntry;
import com.hsmart.payment.infrastructure.persistence.SystemAccountRepository;
import com.hsmart.payment.infrastructure.persistence.SystemLedgerRepository;
import com.hsmart.payment.service.SystemAccountService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SystemAccountServiceImpl implements SystemAccountService {

    /** Code of the singleton platform wallet. */
    public static final String PLATFORM_ACCOUNT_CODE = "PLATFORM";
    private static final String REFERENCE_TYPE_ORDER = "ORDER";

    private final SystemAccountRepository systemAccountRepository;
    private final SystemLedgerRepository systemLedgerRepository;

    @Override
    public void creditPlatformFee(Long orderId, BigDecimal amount, String description) {
        if (amount == null || amount.signum() <= 0) {
            log.warn("Skipping platform-fee credit for order {} because amount is {}", orderId, amount);
            return;
        }
        if (systemLedgerRepository.existsByEntryTypeAndReferenceTypeAndReferenceId(
                LedgerEntryType.PLATFORM_FEE_IN, REFERENCE_TYPE_ORDER, orderId)) {
            log.info("Platform fee for order {} already credited; skipping", orderId);
            return;
        }

        SystemAccount account = getOrCreatePlatformAccount();
        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        systemAccountRepository.save(account);

        SystemLedgerEntry entry = SystemLedgerEntry.builder()
                .entryType(LedgerEntryType.PLATFORM_FEE_IN)
                .direction(LedgerDirection.CREDIT)
                .amount(amount)
                .balanceAfter(newBalance)
                .referenceType(REFERENCE_TYPE_ORDER)
                .referenceId(orderId)
                .description(description)
                .build();
        systemLedgerRepository.save(entry);

        log.info("Credited platform fee {} for order {}; system balance is now {}", amount, orderId, newBalance);
    }

    @Override
    @Transactional(readOnly = true)
    public SystemAccountSummaryDTO getSummary() {
        BigDecimal balance = systemAccountRepository.findByCode(PLATFORM_ACCOUNT_CODE)
                .map(SystemAccount::getBalance)
                .orElse(BigDecimal.ZERO);
        return SystemAccountSummaryDTO.builder()
                .balance(balance)
                .totalCredited(systemLedgerRepository.sumAmountByDirection(LedgerDirection.CREDIT))
                .totalDebited(systemLedgerRepository.sumAmountByDirection(LedgerDirection.DEBIT))
                .entryCount(systemLedgerRepository.count())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<SystemLedgerEntryDTO> getLedger(int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? 20 : size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponseDTO.from(systemLedgerRepository.findAll(pageable).map(this::toLedgerEntryDTO));
    }

    private SystemLedgerEntryDTO toLedgerEntryDTO(SystemLedgerEntry entry) {
        return SystemLedgerEntryDTO.builder()
                .id(entry.getId())
                .entryType(entry.getEntryType())
                .direction(entry.getDirection())
                .amount(entry.getAmount())
                .balanceAfter(entry.getBalanceAfter())
                .referenceType(entry.getReferenceType())
                .referenceId(entry.getReferenceId())
                .description(entry.getDescription())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    private SystemAccount getOrCreatePlatformAccount() {
        return systemAccountRepository.findByCode(PLATFORM_ACCOUNT_CODE)
                .orElseGet(() -> systemAccountRepository.save(SystemAccount.builder()
                        .code(PLATFORM_ACCOUNT_CODE)
                        .balance(BigDecimal.ZERO)
                        .build()));
    }
}
