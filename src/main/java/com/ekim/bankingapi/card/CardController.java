package com.ekim.bankingapi.card;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final CreditCardService creditCardService;

    @PostMapping("/api/v1/accounts/{accountId}/cards")
    public ResponseEntity<CardIssuedResponse> issueCard(
            @PathVariable Long accountId,
            @Valid @RequestBody(required = false) CardIssueRequest request
    ) {
        CardIssueRequest effectiveRequest = request == null ? new CardIssueRequest() : request;
        return ResponseEntity.status(HttpStatus.CREATED).body(cardService.issueCard(accountId, effectiveRequest));
    }

    @GetMapping("/api/v1/accounts/{accountId}/cards")
    public ResponseEntity<List<CardResponse>> getCardsForAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(cardService.getCardsForAccount(accountId));
    }

    @GetMapping("/api/v1/cards/{id}")
    public ResponseEntity<CardResponse> getCardById(@PathVariable Long id) {
        return ResponseEntity.ok(cardService.getCardById(id));
    }

    @PatchMapping("/api/v1/cards/{id}/block")
    public ResponseEntity<CardResponse> blockCard(@PathVariable Long id) {
        return ResponseEntity.ok(cardService.blockCard(id));
    }

    @PatchMapping("/api/v1/cards/{id}/activate")
    public ResponseEntity<CardResponse> activateCard(@PathVariable Long id) {
        return ResponseEntity.ok(cardService.activateCard(id));
    }

    @DeleteMapping("/api/v1/cards/{id}")
    public ResponseEntity<CardResponse> cancelCard(@PathVariable Long id) {
        return ResponseEntity.ok(cardService.cancelCard(id));
    }

    @PostMapping("/api/v1/cards/{id}/charge")
    public ResponseEntity<CreditCardTransactionResponse> charge(
            @PathVariable Long id, @Valid @RequestBody CreditChargeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(creditCardService.charge(id, request));
    }

    @PostMapping("/api/v1/cards/{id}/pay")
    public ResponseEntity<CreditCardTransactionResponse> pay(
            @PathVariable Long id, @Valid @RequestBody CreditPaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(creditCardService.pay(id, request));
    }

    @GetMapping("/api/v1/cards/{id}/statements")
    public ResponseEntity<List<CreditCardStatementResponse>> getStatements(@PathVariable Long id) {
        return ResponseEntity.ok(creditCardService.getStatements(id));
    }

    @GetMapping("/api/v1/cards/{id}/transactions")
    public ResponseEntity<List<CreditCardTransactionResponse>> getTransactions(@PathVariable Long id) {
        return ResponseEntity.ok(creditCardService.getTransactions(id));
    }
}
