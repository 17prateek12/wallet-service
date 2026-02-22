package com.example.walletService.service;

import com.example.walletService.cache.BalanceCache;
import com.example.walletService.dto.TransactionHistoryItem;
import com.example.walletService.dto.TransactionRequest;
import com.example.walletService.idempotency.IdempotencyCache;
import com.example.walletService.entity.LedgerEntry;
import com.example.walletService.exception.WalletException;
import com.example.walletService.entity.Transaction;
import com.example.walletService.entity.Wallet;
import com.example.walletService.repository.LedgerRepository;
import com.example.walletService.repository.TransactionRepository;
import com.example.walletService.repository.WalletRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepo;
    private final TransactionRepository txRepo;
    private final LedgerRepository ledgerRepo;
    private final IdempotencyCache idempotencyCache;
    private final BalanceCache balanceCache;
    private final TransactionTemplate transactionTemplate;

    @Value("${wallet.concurrency.retry.max-attempts:3}")
    private int retryMaxAttempts;
    @Value("${wallet.concurrency.retry.base-backoff-ms:50}")
    private int retryBaseBackoffMs;

    private static final Long TREASURY_WALLET = 1L;
    private static final Long BONUS_WALLET = 2L;
    private static final Long REVENUE_WALLET = 3L;

    public Long topup(TransactionRequest req, String idempotencyKey) {
        return process(req, idempotencyKey, "TOPUP", TREASURY_WALLET, userIdToWalletId(req.userId()));
    }

    public Long bonus(TransactionRequest req, String idempotencyKey) {
        return process(req, idempotencyKey, "BONUS", BONUS_WALLET, userIdToWalletId(req.userId()));
    }

    public Long spend(TransactionRequest req, String idempotencyKey) {
        return process(req, idempotencyKey, "SPEND", userIdToWalletId(req.userId()), REVENUE_WALLET);
    }

    /**
     * Returns transaction history for the user's wallet (ledger entries for their wallet, with tx type/status).
     * Ordered by most recent first. Amount is the effect on this wallet (positive = credit, negative = debit).
     */
    @Transactional(readOnly = true)
    public List<TransactionHistoryItem> getTransactionHistory(Long userId) {
        Long walletId = userIdToWalletId(userId);
        List<LedgerEntry> entries = ledgerRepo.findByWalletIdOrderByCreatedAtDesc(walletId);
        if (entries.isEmpty()) return List.of();
        List<Long> txIds = entries.stream().map(LedgerEntry::getTransactionId).distinct().toList();
        List<Transaction> transactions = txRepo.findAllById(txIds);
        var txMap = transactions.stream().collect(Collectors.toMap(Transaction::getId, t -> t));
        return entries.stream()
                .map(e -> {
                    Transaction tx = txMap.get(e.getTransactionId());
                    return new TransactionHistoryItem(
                            e.getTransactionId(),
                            tx != null ? tx.getType() : null,
                            tx != null ? tx.getStatus() : null,
                            e.getAmount(),
                            e.getCreatedAt() != null ? e.getCreatedAt() : (tx != null ? tx.getCreatedAt() : null)
                    );
                })
                .toList();
    }

    /**
     * Process a transaction with double-entry ledger and deadlock-safe locking.
     * Idempotency: validate key first (controller), then check cache (fast), claim for parallel safety, then DB.
     * Sequential and parallel duplicates return the same transaction id; only one request performs the work.
     */
    private Long process(TransactionRequest req, String idempotencyKey, String type, Long fromWalletId, Long toWalletId) {

        // --- 1. Cache check (fast path for distributed / retries) ---
        if (idempotencyCache.isActive()) {
            Long cachedTxId = idempotencyCache.get(idempotencyKey);
            if (cachedTxId != null) {
                return cachedTxId;
            }
            if (!idempotencyCache.tryClaim(idempotencyKey)) {
                Long again = idempotencyCache.get(idempotencyKey);
                if (again != null) return again;
                throw new WalletException("Another request is processing this idempotency key; retry shortly", 409);
            }
        }

        int attempts = 0;
        while (true) {
            try {
                return transactionTemplate.execute(status ->
                        doProcess(idempotencyKey, req, type, fromWalletId, toWalletId));
            } catch (DataIntegrityViolationException e) {
                Long existingId = transactionTemplate.execute(s ->
                        txRepo.findByIdempotencyKey(idempotencyKey).map(Transaction::getId).orElse(null));
                if (existingId != null) {
                    if (idempotencyCache.isActive()) {
                        idempotencyCache.put(idempotencyKey, existingId, IdempotencyCache.SUCCESS_TTL_SECONDS);
                    }
                    return existingId;
                }
                throw e;
            } catch (Exception e) {
                boolean retryable = LockRetryHelper.isRetryable(e);
                if (!retryable || attempts >= retryMaxAttempts - 1) {
                    if (idempotencyCache.isActive()) {
                        idempotencyCache.delete(idempotencyKey);
                    }
                    throw e;
                }
                try {
                    Thread.sleep(LockRetryHelper.backoffMs(attempts, retryBaseBackoffMs));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    if (idempotencyCache.isActive()) idempotencyCache.delete(idempotencyKey);
                    throw new RuntimeException("Interrupted during retry", ie);
                }
                attempts++;
            }
        }
    }

    /**
     * DB path: insert-first to claim idempotency key (unique constraint); then ledger.
     * Handles parallel requests: second request gets unique violation and returns existing tx id.
     */
    private Long doProcess(String idempotencyKey, TransactionRequest req, String type, Long fromWalletId, Long toWalletId) {

        // --- 2. DB idempotency check (and claim via insert for parallel safety) ---
        Optional<Transaction> existing = txRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Long txId = existing.get().getId();
            if (idempotencyCache.isActive()) {
                idempotencyCache.put(idempotencyKey, txId, IdempotencyCache.SUCCESS_TTL_SECONDS);
            }
            return txId;
        }

        Transaction tx = new Transaction();
        tx.setType(type);
        tx.setStatus("PENDING");
        tx.setIdempotencyKey(idempotencyKey);
        try {
            txRepo.saveAndFlush(tx);
        } catch (DataIntegrityViolationException e) {
            Transaction existingTx = txRepo.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            if (idempotencyCache.isActive()) {
                idempotencyCache.put(idempotencyKey, existingTx.getId(), IdempotencyCache.SUCCESS_TTL_SECONDS);
            }
            return existingTx.getId();
        }

        // --- Deadlock avoidance: always lock in ascending wallet ID order ---
        Long firstId = Math.min(fromWalletId, toWalletId);
        Long secondId = Math.max(fromWalletId, toWalletId);
        Wallet first = walletRepo.lockWalletForUpdate(firstId);
        Wallet second = walletRepo.lockWalletForUpdate(secondId);
        Wallet srcWallet = first.getId().equals(fromWalletId) ? first : second;
        Wallet destWallet = first.getId().equals(toWalletId) ? first : second;

        Long amount = req.amount();

        // --- Check funds only for spending ---
        if (type.equals("SPEND")) {
            Long balance = ledgerRepo.getBalance(srcWallet.getId());
            if (balance < amount) {
                throw new WalletException("Insufficient funds", 400);
            }
        }

        // --- Create Ledger Entries (Double Entry) ---
        LedgerEntry debit = LedgerEntry.builder()
                .transactionId(tx.getId())
                .walletId(srcWallet.getId())
                .amount(-amount)
                .build();

        LedgerEntry credit = LedgerEntry.builder()
                .transactionId(tx.getId())
                .walletId(destWallet.getId())
                .amount(amount)
                .build();

        ledgerRepo.save(debit);
        ledgerRepo.save(credit);

        // --- Finalize transaction ---
        tx.setStatus("SUCCESS");
        txRepo.save(tx);

        if (idempotencyCache.isActive()) {
            idempotencyCache.put(idempotencyKey, tx.getId(), IdempotencyCache.SUCCESS_TTL_SECONDS);
        }
        if (balanceCache.isActive()) {
            balanceCache.invalidate(fromWalletId);
            balanceCache.invalidate(toWalletId);
        }
        return tx.getId();
    }

    private Long userIdToWalletId(Long userId) {
        return walletRepo.findByUserId(userId)
                .map(Wallet::getId)
                .orElseThrow(() -> new WalletException("User wallet not found", 404));
    }
}
