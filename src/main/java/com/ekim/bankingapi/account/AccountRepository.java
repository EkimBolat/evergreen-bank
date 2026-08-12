package com.ekim.bankingapi.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByCustomerId(Long customerId);

    boolean existsByCustomerId(Long customerId);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findByAccountType(AccountType accountType);
}