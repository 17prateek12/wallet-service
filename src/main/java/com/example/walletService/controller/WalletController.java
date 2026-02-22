package com.example.walletService.controller;

import com.example.walletService.cache.BalanceCache;
import com.example.walletService.dto.TransactionHistoryItem;
import com.example.walletService.dto.TransactionRequest;
import com.example.walletService.dto.TransactionResponse;
import com.example.walletService.entity.Wallet;
import com.example.walletService.exception.WalletException;
import com.example.walletService.repository.LedgerRepository;
import com.example.walletService.repository.WalletRepository;
import com.example.walletService.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int IDEMPOTENCY_KEY_MIN = 1;
    private static final int IDEMPOTENCY_KEY_MAX = 200;

    private final WalletService walletService;
    private final LedgerRepository ledgerRepo;
    private final WalletRepository walletRepo;
    private final BalanceCache balanceCache;

    @PostMapping("/topup")
    public ResponseEntity<TransactionResponse> topup(
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKeyHeader,
            @Valid @RequestBody TransactionRequest req) {
        String key = resolveAndValidateIdempotencyKey(idempotencyKeyHeader, req.idempotencyKey());
        return ResponseEntity.ok(new TransactionResponse(walletService.topup(req, key)));
    }

    @PostMapping("/bonus")
    public ResponseEntity<TransactionResponse> bonus(
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKeyHeader,
            @Valid @RequestBody TransactionRequest req) {
        String key = resolveAndValidateIdempotencyKey(idempotencyKeyHeader, req.idempotencyKey());
        return ResponseEntity.ok(new TransactionResponse(walletService.bonus(req, key)));
    }

    @PostMapping("/spend")
    public ResponseEntity<TransactionResponse> spend(
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKeyHeader,
            @Valid @RequestBody TransactionRequest req) {
        String key = resolveAndValidateIdempotencyKey(idempotencyKeyHeader, req.idempotencyKey());
        return ResponseEntity.ok(new TransactionResponse(walletService.spend(req, key)));
    }

    /**
     * Validate idempotency key from header or body (header takes precedence), then allow processing.
     * Ensures key is present and 1–200 chars before any business logic runs.
     */
    private String resolveAndValidateIdempotencyKey(String fromHeader, String fromBody) {
        String key = (fromHeader != null && !fromHeader.isBlank())
                ? fromHeader.trim()
                : (fromBody != null ? fromBody.trim() : null);
        if (key == null || key.isEmpty()) {
            throw new WalletException("Idempotency key is required (header Idempotency-Key or body idempotencyKey)", 400);
        }
        if (key.length() > IDEMPOTENCY_KEY_MAX) {
            throw new WalletException("Idempotency key must be 1–200 characters", 400);
        }
        return key;
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<Map<String, Long>> balance(@PathVariable Long userId) {
        Wallet wallet = walletRepo.findByUserId(userId)
                .orElseThrow(() -> new WalletException("User wallet not found", 404));
        Long balance;
        if (balanceCache.isActive()) {
            Long cached = balanceCache.get(wallet.getId());
            if (cached != null) {
                return ResponseEntity.ok(Map.of("userId", userId, "balance", cached));
            }
            balance = ledgerRepo.getBalance(wallet.getId());
            balanceCache.put(wallet.getId(), balance);
        } else {
            balance = ledgerRepo.getBalance(wallet.getId());
        }
        return ResponseEntity.ok(Map.of("userId", userId, "balance", balance));
    }

    @GetMapping("/{userId}/transactions")
    public ResponseEntity<List<TransactionHistoryItem>> transactionHistory(@PathVariable Long userId) {
        return ResponseEntity.ok(walletService.getTransactionHistory(userId));
    }
}
