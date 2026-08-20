package com.ekim.bankingapi.nature;

import com.ekim.bankingapi.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/nature")
@RequiredArgsConstructor
public class NatureController {

    private final NatureService natureService;

    @GetMapping("/certificates/me")
    public ResponseEntity<List<TreeCertificateResponse>> getMyCertificates() {
        return ResponseEntity.ok(natureService.getCertificatesForCustomer(currentCustomerId()));
    }

    private Long currentCustomerId() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(details instanceof Long customerId)) {
            throw new InvalidCredentialsException("Unable to resolve authenticated customer");
        }
        return customerId;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(Map.of("totalTreesPlanted", natureService.getTotalTreesPlanted()));
    }
}