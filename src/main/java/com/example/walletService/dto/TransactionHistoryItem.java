package com.example.walletService.dto;

import java.time.LocalDateTime;

/**
 * One entry in a user's transaction history. Amount is the effect on this user's wallet
 * (positive = credit, e.g. topup/bonus; negative = debit, e.g. spend).
 */
public record TransactionHistoryItem(
        Long transactionId,
        String type,
        String status,
        Long amount,
        LocalDateTime createdAt
) {}
