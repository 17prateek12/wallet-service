package com.example.walletService.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionRequest(
        @NotNull(message = "userId is required") Long userId,
        @NotNull @Min(1) Long amount,
        @Size(min = 1, max = 200) String idempotencyKey  // optional when Idempotency-Key header is used
) {}