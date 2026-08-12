package com.ekim.bankingapi.scheduledtransfer;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.account.AccountService;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import com.ekim.bankingapi.transfer.TransferRequest;
import com.ekim.bankingapi.transfer.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTransferService {

    private final ScheduledTransferRepository scheduledTransferRepository;
    private final AccountService accountService;
    private final TransferService transferService;
    private final NotificationService notificationService;

    public ScheduledTransferResponse createScheduledTransfer(ScheduledTransferRequest request) {
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new InvalidRequestException("Cannot schedule a transfer to the same account");
        }

        Account fromAccount = accountService.findAccountEntityById(request.getFromAccountId());
        Account toAccount = accountService.findAccountEntityById(request.getToAccountId());

        ScheduledTransfer scheduledTransfer = new ScheduledTransfer();
        scheduledTransfer.setFromAccount(fromAccount);
        scheduledTransfer.setToAccount(toAccount);
        scheduledTransfer.setAmount(request.getAmount());
        scheduledTransfer.setFrequency(request.getFrequency());
        scheduledTransfer.setNextExecutionDate(calculateNextDate(LocalDate.now(), request.getFrequency()));
        scheduledTransfer.setActive(true);

        ScheduledTransfer saved = scheduledTransferRepository.save(scheduledTransfer);
        return ScheduledTransferResponse.fromEntity(saved);
    }

    public List<ScheduledTransferResponse> getScheduledTransfersForCustomer(Long customerId) {
        return scheduledTransferRepository.findByFromAccountCustomerId(customerId).stream()
                .map(ScheduledTransferResponse::fromEntity)
                .toList();
    }

    public void cancelScheduledTransfer(Long id) {
        ScheduledTransfer scheduledTransfer = scheduledTransferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled transfer not found with id: " + id));
        scheduledTransfer.setActive(false);
        scheduledTransferRepository.save(scheduledTransfer);
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void executeDueTransfers() {
        List<ScheduledTransfer> dueTransfers = scheduledTransferRepository
                .findByActiveTrueAndNextExecutionDateLessThanEqual(LocalDate.now());

        log.info("Executing {} due scheduled transfer(s)", dueTransfers.size());

        for (ScheduledTransfer scheduled : dueTransfers) {
            try {
                TransferRequest transferRequest = new TransferRequest();
                transferRequest.setFromAccountId(scheduled.getFromAccount().getId());
                transferRequest.setToAccountId(scheduled.getToAccount().getId());
                transferRequest.setAmount(scheduled.getAmount());

                transferService.transfer(transferRequest);

                scheduled.setNextExecutionDate(calculateNextDate(scheduled.getNextExecutionDate(), scheduled.getFrequency()));
                scheduledTransferRepository.save(scheduled);

                notificationService.notify(scheduled.getFromAccount().getCustomer().getId(), NotificationType.SCHEDULED_TRANSFER_EXECUTED,
                        "Scheduled Transfer Executed", "Your scheduled transfer of " + scheduled.getAmount() +
                                " to account " + scheduled.getToAccount().getAccountNumber() + " was completed.");

                log.info("Scheduled transfer executed successfully: id={}", scheduled.getId());
            } catch (Exception e) {
                log.error("Scheduled transfer failed: id={}, reason={}", scheduled.getId(), e.getMessage());
                notificationService.notify(scheduled.getFromAccount().getCustomer().getId(), NotificationType.SCHEDULED_TRANSFER_FAILED,
                        "Scheduled Transfer Failed", "Your scheduled transfer of " + scheduled.getAmount() +
                                " to account " + scheduled.getToAccount().getAccountNumber() + " failed: " + e.getMessage());
            }
        }
    }

    private LocalDate calculateNextDate(LocalDate from, Frequency frequency) {
        return switch (frequency) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
        };
    }
}