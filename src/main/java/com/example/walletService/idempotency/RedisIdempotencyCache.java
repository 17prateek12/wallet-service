package com.example.walletService.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "wallet.idempotency.redis.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RedisIdempotencyCache implements IdempotencyCache {

    private final StringRedisTemplate redis;

    @Override
    public Long get(String key) {
        String k = PREFIX + key;
        String val = redis.opsForValue().get(k);
        if (val == null) return null;
        if (CLAIM_VALUE.equals(val)) return null; // still processing
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean tryClaim(String key) {
        String k = PREFIX + key;
        Boolean set = redis.opsForValue().setIfAbsent(k, CLAIM_VALUE, CLAIM_TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(set);
    }

    @Override
    public void put(String key, long txId, int ttlSeconds) {
        String k = PREFIX + key;
        redis.opsForValue().set(k, String.valueOf(txId), ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void delete(String key) {
        redis.delete(PREFIX + key);
    }

    @Override
    public boolean isActive() {
        return true;
    }
}
