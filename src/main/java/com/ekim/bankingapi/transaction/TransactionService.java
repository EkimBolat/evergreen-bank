package com.ekim.bankingapi.transaction;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.account.AccountService;
import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.exception.InsufficientBalanceException;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.nature.NatureService;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final NatureService natureService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Transactional
    public TransactionResponse deposit(Long accountId, BigDecimal amount) {
        validateAmount(amount);

        Account account = accountService.findAccountEntityById(accountId);

        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);

        Transaction transaction = saveTransaction(account, TransactionType.DEPOSIT, amount, newBalance);
        natureService.awardPointsForTransaction(account.getCustomer().getId(), amount);

        auditLogService.log("Transaction", transaction.getId(), "DEPOSIT",
                "Account: " + account.getAccountNumber() + ", Amount: " + amount);

        notificationService.notify(account.getCustomer().getId(), NotificationType.DEPOSIT,
                "Deposit Received", "Your account " + account.getAccountNumber() + " was credited " + amount + ". New balance: " + newBalance);

        log.info("Deposit successful: accountId={}, amount={}, newBalance={}", accountId, amount, newBalance);
        return TransactionResponse.fromEntity(transaction);
    }

    @Transactional
    public TransactionResponse withdraw(Long accountId, BigDecimal amount) {
        validateAmount(amount);

        Account account = accountService.findAccountEntityById(accountId);

        if (account.getBalance().compareTo(amount) < 0) {
            log.warn("Withdrawal failed - insufficient balance: accountId={}, requested={}, available={}",
                    accountId, amount, account.getBalance());
            throw new InsufficientBalanceException("Insufficient balance. Current balance: " + account.getBalance());
        }

        accountService.checkAndRecordWithdrawalLimit(account, amount);

        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.setBalance(newBalance);

        Transaction transaction = saveTransaction(account, TransactionType.WITHDRAWAL, amount, newBalance);
        natureService.awardPointsForTransaction(account.getCustomer().getId(), amount);

        auditLogService.log("Transaction", transaction.getId(), "WITHDRAWAL",
                "Account: " + account.getAccountNumber() + ", Amount: " + amount);

        notificationService.notify(account.getCustomer().getId(), NotificationType.WITHDRAWAL,
                "Withdrawal Completed", "Your account " + account.getAccountNumber() + " was debited " + amount + ". New balance: " + newBalance);

        log.info("Withdrawal successful: accountId={}, amount={}, newBalance={}", accountId, amount, newBalance);
        return TransactionResponse.fromEntity(transaction);
    }

    public Page<TransactionResponse> getTransactionHistory(Long accountId, Pageable pageable) {
        return transactionRepository.findByAccountId(accountId, pageable)
                .map(TransactionResponse::fromEntity);
    }

    private Transaction saveTransaction(Account account, TransactionType type, BigDecimal amount, BigDecimal balanceAfter) {
        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        return transactionRepository.save(transaction);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Amount must be greater than zero");
        }
    }
}