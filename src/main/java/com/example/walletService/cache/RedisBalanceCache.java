package com.example.walletService.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed balance cache. Used when wallet.idempotency.redis.enabled=true (same Redis as idempotency).
 * GET balance checks cache first; on miss loads from DB and caches. Writes invalidate the affected wallets.
 */
@Component
@ConditionalOnProperty(name = "wallet.idempotency.redis.enabled", havingValue = "true")
public class RedisBalanceCache implements BalanceCache {

    private final StringRedisTemplate redis;

    public RedisBalanceCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Long get(Long walletId) {
        if (walletId == null) return null;
        String key = KEY_PREFIX + walletId;
        String val = redis.opsForValue().get(key);
        if (val == null) return null;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void put(Long walletId, long balance) {
        if (walletId == null) return;
        String key = KEY_PREFIX + walletId;
        redis.opsForValue().set(key, String.valueOf(balance), TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void invalidate(Long walletId) {
        if (walletId == null) return;
        redis.delete(KEY_PREFIX + walletId);
    }

    @Override
    public boolean isActive() {
        return true;
    }
}
