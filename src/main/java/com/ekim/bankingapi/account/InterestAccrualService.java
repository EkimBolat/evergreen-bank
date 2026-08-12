package com.ekim.bankingapi.account;

import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import com.ekim.bankingapi.transaction.Transaction;
import com.ekim.bankingapi.transaction.TransactionRepository;
import com.ekim.bankingapi.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestAccrualService {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 2 1 * *")
    public void applyMonthlyInterest() {
        List<Account> savingsAccounts = accountRepository.findByAccountType(AccountType.SAVINGS);
        log.info("Applying monthly interest to {} savings account(s)", savingsAccounts.size());

        for (Account account : savingsAccounts) {
            try {
                applyInterest(account);
            } catch (Exception e) {
                log.error("Interest accrual failed: accountId={}, reason={}", account.getId(), e.getMessage());
            }
        }
    }

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
