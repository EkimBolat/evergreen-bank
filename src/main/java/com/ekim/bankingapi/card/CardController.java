package com.ekim.bankingapi.card;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @PostMapping("/api/v1/accounts/{accountId}/cards")
    public ResponseEntity<CardIssuedResponse> issueCard(@PathVariable Long accountId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cardService.issueCard(accountId));
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
}
