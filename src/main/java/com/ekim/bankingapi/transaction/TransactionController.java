package com.ekim.bankingapi.transaction;

import com.ekim.bankingapi.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;

    @PostMapping("/deposit/{accountId}")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody AmountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (idempotencyKey != null) {
            var cached = idempotencyService.getCachedResponse(idempotencyKey);
            if (cached.isPresent()) {
                return ResponseEntity.status(HttpStatus.CREATED).body((TransactionResponse) cached.get());
            }
        }

        TransactionResponse transaction = transactionService.deposit(accountId, request.getAmount());

        if (idempotencyKey != null) {
            idempotencyService.storeResponse(idempotencyKey, transaction);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }

    @PostMapping("/withdraw/{accountId}")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable Long accountId,
            @Valid @RequestBody AmountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (idempotencyKey != null) {
            var cached = idempotencyService.getCachedResponse(idempotencyKey);
            if (cached.isPresent()) {
                return ResponseEntity.status(HttpStatus.CREATED).body((TransactionResponse) cached.get());
            }
        }

        TransactionResponse transaction = transactionService.withdraw(accountId, request.getAmount());

        if (idempotencyKey != null) {
            idempotencyService.storeResponse(idempotencyKey, transaction);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<Page<TransactionResponse>> getHistory(
            @PathVariable Long accountId,
            @PageableDefault(sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountId, pageable));
    }

    @GetMapping("/account/{accountId}/export")
    public ResponseEntity<byte[]> exportStatement(@PathVariable Long accountId) {
        byte[] csv = transactionService.exportStatementCsv(accountId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"statement-account-" + accountId + ".csv\"")
                .body(csv);
    }
}