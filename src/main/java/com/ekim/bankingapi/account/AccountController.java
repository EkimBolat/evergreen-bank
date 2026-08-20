package com.ekim.bankingapi.account;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/customer/{customerId}")
    public ResponseEntity<AccountResponse> createAccount(@PathVariable Long customerId, @Valid @RequestBody AccountRequest request) {
        AccountResponse created = accountService.createAccount(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/me")
    public ResponseEntity<AccountResponse> getMyAccount() {
        return ResponseEntity.ok(accountService.getMyAccount());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<AccountLookupResponse> getAccountByNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccountByNumber(accountNumber));
    }

    @GetMapping
    public ResponseEntity<Page<AccountResponse>> getAllAccounts(Pageable pageable) {
        return ResponseEntity.ok(accountService.getAllAccounts(pageable));
    }

    @PutMapping("/{id}/limits")
    public ResponseEntity<AccountResponse> updateLimits(@PathVariable Long id, @Valid @RequestBody AccountLimitRequest request) {
        return ResponseEntity.ok(accountService.updateLimits(id, request));
    }
}