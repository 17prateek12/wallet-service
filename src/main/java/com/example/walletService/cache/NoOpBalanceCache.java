package com.example.walletService.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * No-op balance cache when Redis is not enabled. All reads go to DB.
 */
@Component
@ConditionalOnMissingBean(RedisBalanceCache.class)
public class NoOpBalanceCache implements BalanceCache {

    @Override
    public Long get(Long walletId) {
        return null;
    }

    @Override
    public void put(Long walletId, long balance) {
        // no-op
    }

    @Override
    public void invalidate(Long walletId) {
        // no-op
    }

    @Override
    public boolean isActive() {
        return false;
    }
}
