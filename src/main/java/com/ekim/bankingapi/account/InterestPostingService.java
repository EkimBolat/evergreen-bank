package com.ekim.bankingapi.account;

import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import com.ekim.bankingapi.transaction.Transaction;
import com.ekim.bankingapi.transaction.TransactionRepository;
import com.ekim.bankingapi.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Holds the per-account interest posting logic as its own bean so {@code @Transactional}
 * is honored — {@link InterestAccrualService} calls into this bean from its scheduled loop,
 * which goes through the Spring proxy (unlike a same-class self-invocation would).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterestPostingService {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Transactional
    public void applyInterest(Account account) {
        if (account.getInterestRate() == null || account.getInterestRate().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal monthlyRate = account.getInterestRate()
                .divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP)
                .divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP);
        BigDecimal interest = account.getBalance().multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);

        if (interest.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal newBalance = account.getBalance().add(interest);
        account.setBalance(newBalance);
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setType(TransactionType.INTEREST);
        transaction.setAmount(interest);
        transaction.setBalanceAfter(newBalance);
        transactionRepository.save(transaction);

        auditLogService.log("Account", account.getId(), "INTEREST_ACCRUAL",
                "Account: " + account.getAccountNumber() + ", Interest: " + interest);

        notificationService.notify(account.getCustomer().getId(), NotificationType.INTEREST_CREDITED,
                "Interest Credited", "Your savings account " + account.getAccountNumber() +
                        " was credited " + interest + " in interest. New balance: " + newBalance);

        log.info("Interest applied: accountId={}, interest={}, newBalance={}", account.getId(), interest, newBalance);
    }
}
