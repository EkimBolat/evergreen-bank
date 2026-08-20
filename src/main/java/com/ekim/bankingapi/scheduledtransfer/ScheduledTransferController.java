package com.ekim.bankingapi.scheduledtransfer;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scheduled-transfers")
@RequiredArgsConstructor
public class ScheduledTransferController {

    private final ScheduledTransferService scheduledTransferService;

    @PostMapping
    public ResponseEntity<ScheduledTransferResponse> createScheduledTransfer(@Valid @RequestBody ScheduledTransferRequest request) {
        ScheduledTransferResponse created = scheduledTransferService.createScheduledTransfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/me")
    public ResponseEntity<List<ScheduledTransferResponse>> getMyScheduledTransfers() {
        return ResponseEntity.ok(scheduledTransferService.getMyScheduledTransfers());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelScheduledTransfer(@PathVariable Long id) {
        scheduledTransferService.cancelScheduledTransfer(id);
        return ResponseEntity.noContent().build();
    }
}