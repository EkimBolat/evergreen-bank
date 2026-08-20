package com.ekim.bankingapi.account;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Getter
@AllArgsConstructor
public class AccountResponse {

    private Long id;
    private String accountNumber;
    private BigDecimal balance;
    private AccountType accountType;
    private BigDecimal interestRate;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal dailyLimitRemaining;
    private BigDecimal monthlyLimitRemaining;
    private Long branchId;
    private String branchName;
    private Long customerId;
    private String customerFullName;
    private LocalDateTime createdAt;

    public static AccountResponse fromEntity(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getAccountType(),
                account.getInterestRate(),
                account.getDailyLimit(),
                account.getMonthlyLimit(),
                account.getDailyLimit().subtract(effectiveDailyWithdrawn(account)),
                account.getMonthlyLimit().subtract(effectiveMonthlyWithdrawn(account)),
                account.getBranch() != null ? account.getBranch().getId() : null,
                account.getBranch() != null ? account.getBranch().getName() : null,
                account.getCustomer().getId(),
                account.getCustomer().getFirstName() + " " + account.getCustomer().getLastName(),
                account.getCreatedAt()
        );
    }

    // dailyWithdrawnAmount/monthlyWithdrawnAmount on the entity are only reset lazily on the
    // account's next withdrawal, so a stale value from a previous day/month must count as 0 here.
    private static BigDecimal effectiveDailyWithdrawn(Account account) {
        if (account.getLastWithdrawalDate() == null || !account.getLastWithdrawalDate().isEqual(LocalDate.now())) {
            return BigDecimal.ZERO;
        }
        return account.getDailyWithdrawnAmount();
    }

    private static BigDecimal effectiveMonthlyWithdrawn(Account account) {
        if (account.getLastWithdrawalMonth() == null
                || !account.getLastWithdrawalMonth().equals(YearMonth.now().toString())) {
            return BigDecimal.ZERO;
        }
        return account.getMonthlyWithdrawnAmount();
    }
}