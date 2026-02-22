package com.example.walletService.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * No-op cache when Redis is not enabled. Idempotency is handled only via DB
 * (unique constraint + insert-first / find on duplicate).
 */
@Component
@ConditionalOnMissingBean(IdempotencyCache.class)
public class NoOpIdempotencyCache implements IdempotencyCache {

    @Override
    public Long get(String key) {
        return null;
    }

    @Override
    public boolean tryClaim(String key) {
        return true; // no claim; DB will enforce via unique constraint
    }

    @Override
    public void put(String key, long txId, int ttlSeconds) {
        // no-op
    }

    @Override
    public void delete(String key) {
        // no-op
    }

    @Override
    public boolean isActive() {
        return false;
    }
}
