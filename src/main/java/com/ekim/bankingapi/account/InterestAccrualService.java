package com.ekim.bankingapi.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestAccrualService {

    private final AccountRepository accountRepository;
    private final InterestPostingService interestPostingService;

    @Scheduled(cron = "0 0 2 1 * *")
    public void applyMonthlyInterest() {
        List<Account> savingsAccounts = accountRepository.findByAccountType(AccountType.SAVINGS);
        log.info("Applying monthly interest to {} savings account(s)", savingsAccounts.size());

        for (Account account : savingsAccounts) {
            try {
                interestPostingService.applyInterest(account);
            } catch (Exception e) {
                log.error("Interest accrual failed: accountId={}, reason={}", account.getId(), e.getMessage());
            }
        }
    }
}
