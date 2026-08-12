package com.ekim.bankingapi.transfer;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.account.AccountService;
import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.exception.InsufficientBalanceException;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.nature.NatureService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final NatureService natureService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        Long fromAccountId = request.getFromAccountId();
        Long toAccountId = request.getToAccountId();
        BigDecimal amount = request.getAmount();

        if (fromAccountId.equals(toAccountId)) {
            log.warn("Transfer rejected - same account: accountId={}", fromAccountId);
            throw new InvalidRequestException("Cannot transfer to the same account");
        }

        Account fromAccount = accountService.findAccountEntityById(fromAccountId);
        Account toAccount = accountService.findAccountEntityById(toAccountId);

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            log.warn("Transfer rejected - insufficient balance: fromAccountId={}, requested={}, available={}",
                    fromAccountId, amount, fromAccount.getBalance());
            throw new InsufficientBalanceException("Insufficient balance in source account");
        }

        accountService.checkAndRecordWithdrawalLimit(fromAccount, amount);

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        Transfer transfer = new Transfer();
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setAmount(amount);
        Transfer savedTransfer = transferRepository.save(transfer);

        recordTransaction(fromAccount, TransactionType.WITHDRAWAL, amount, fromAccount.getBalance(), savedTransfer.getId());
        recordTransaction(toAccount, TransactionType.DEPOSIT, amount, toAccount.getBalance(), savedTransfer.getId());

        natureService.awardPointsForTransaction(fromAccount.getCustomer().getId(), amount);
        natureService.awardPointsForTransaction(toAccount.getCustomer().getId(), amount);

        auditLogService.log("Transfer", savedTransfer.getId(), "TRANSFER",
                "From: " + fromAccount.getAccountNumber() + ", To: " + toAccount.getAccountNumber() + ", Amount: " + amount);

        notificationService.notify(fromAccount.getCustomer().getId(), NotificationType.TRANSFER_SENT,
                "Transfer Sent", "You sent " + amount + " to account " + toAccount.getAccountNumber());
        notificationService.notify(toAccount.getCustomer().getId(), NotificationType.TRANSFER_RECEIVED,
                "Transfer Received", "You received " + amount + " from account " + fromAccount.getAccountNumber());

        log.info("Transfer successful: transferId={}, fromAccountId={}, toAccountId={}, amount={}",
                savedTransfer.getId(), fromAccountId, toAccountId, amount);

        return TransferResponse.fromEntity(savedTransfer);
    }

    private void recordTransaction(Account account, TransactionType type, BigDecimal amount, BigDecimal balanceAfter, Long transferId) {
        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setTransferId(transferId);
        transactionRepository.save(transaction);
    }
}