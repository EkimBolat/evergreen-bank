package com.ekim.bankingapi.account;

import com.ekim.bankingapi.branch.Branch;
import com.ekim.bankingapi.branch.BranchService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.customer.CustomerService;
import com.ekim.bankingapi.exception.DuplicateResourceException;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.exception.TransactionLimitExceededException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerService customerService;
    private final BranchService branchService;
    private final NotificationService notificationService;
    private static final SecureRandom RANDOM = new SecureRandom();

    public AccountResponse createAccount(Long customerId, AccountRequest request) {
        Customer customer = customerService.findCustomerEntityById(customerId);

        if (accountRepository.existsByCustomerId(customerId)) {
            throw new DuplicateResourceException("Customer already has an account: " + customerId);
        }

        validateAccountTypeRules(request);

        Account account = new Account();
        account.setCustomer(customer);
        account.setBalance(BigDecimal.ZERO);
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setAccountType(request.getAccountType());
        account.setInterestRate(request.getAccountType() == AccountType.SAVINGS ? request.getInterestRate() : null);

        if (request.getBranchId() != null) {
            Branch branch = branchService.findBranchEntityById(request.getBranchId());
            account.setBranch(branch);
        }

        Account saved = accountRepository.save(account);
        return AccountResponse.fromEntity(saved);
    }

    public AccountResponse getAccountById(Long id) {
        Account account = findAccountEntityById(id);
        return AccountResponse.fromEntity(account);
    }

    public AccountResponse getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        return AccountResponse.fromEntity(account);
    }

    public Page<AccountResponse> getAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable)
                .map(AccountResponse::fromEntity);
    }

    // Transaction ve Transfer service'leri, para çıkışından ÖNCE bu kontrolü çağırır
    public void checkAndRecordWithdrawalLimit(Account account, BigDecimal amount) {
        resetCountersIfNeeded(account);

        BigDecimal newDailyTotal = account.getDailyWithdrawnAmount().add(amount);
        if (newDailyTotal.compareTo(account.getDailyLimit()) > 0) {
            notificationService.notify(account.getCustomer().getId(), NotificationType.LIMIT_EXCEEDED,
                    "Daily Limit Exceeded", "An attempted withdrawal of " + amount + " on account " +
                            account.getAccountNumber() + " exceeded your daily limit of " + account.getDailyLimit());
            throw new TransactionLimitExceededException(
                    "Daily withdrawal limit exceeded. Limit: " + account.getDailyLimit() +
                            ", already used: " + account.getDailyWithdrawnAmount());
        }

        BigDecimal newMonthlyTotal = account.getMonthlyWithdrawnAmount().add(amount);
        if (newMonthlyTotal.compareTo(account.getMonthlyLimit()) > 0) {
            notificationService.notify(account.getCustomer().getId(), NotificationType.LIMIT_EXCEEDED,
                    "Monthly Limit Exceeded", "An attempted withdrawal of " + amount + " on account " +
                            account.getAccountNumber() + " exceeded your monthly limit of " + account.getMonthlyLimit());
            throw new TransactionLimitExceededException(
                    "Monthly withdrawal limit exceeded. Limit: " + account.getMonthlyLimit() +
                            ", already used: " + account.getMonthlyWithdrawnAmount());
        }

        account.setDailyWithdrawnAmount(newDailyTotal);
        account.setMonthlyWithdrawnAmount(newMonthlyTotal);
    }

    public AccountResponse updateLimits(Long accountId, AccountLimitRequest request) {
        Account account = findAccountEntityById(accountId);
        account.setDailyLimit(request.getDailyLimit());
        account.setMonthlyLimit(request.getMonthlyLimit());
        Account saved = accountRepository.save(account);
        return AccountResponse.fromEntity(saved);
    }

    private void resetCountersIfNeeded(Account account) {
        LocalDate today = LocalDate.now();
        if (account.getLastWithdrawalDate() == null || !account.getLastWithdrawalDate().isEqual(today)) {
            account.setDailyWithdrawnAmount(BigDecimal.ZERO);
            account.setLastWithdrawalDate(today);
        }

        String currentMonth = YearMonth.now().toString();
        if (account.getLastWithdrawalMonth() == null || !account.getLastWithdrawalMonth().equals(currentMonth)) {
            account.setMonthlyWithdrawnAmount(BigDecimal.ZERO);
            account.setLastWithdrawalMonth(currentMonth);
        }
    }

    private void validateAccountTypeRules(AccountRequest request) {
        if (request.getAccountType() == AccountType.SAVINGS) {
            if (request.getInterestRate() == null || request.getInterestRate().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidRequestException("Savings accounts require a positive interest rate");
            }
        } else if (request.getAccountType() == AccountType.CHECKING) {
            if (request.getInterestRate() != null) {
                throw new InvalidRequestException("Checking accounts cannot have an interest rate");
            }
        }
    }

    private String generateUniqueAccountNumber() {
        String candidate;
        do {
            candidate = generateRandomNumber();
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }

    private String generateRandomNumber() {
        StringBuilder sb = new StringBuilder("TR");
        for (int i = 0; i < 10; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public Account findAccountEntityById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
    }
}