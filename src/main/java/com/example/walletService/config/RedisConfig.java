package com.example.walletService.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Enables Redis only when idempotency cache is configured, so the app starts without Redis by default.
 */
@Configuration
@ConditionalOnProperty(name = "wallet.idempotency.redis.enabled", havingValue = "true")
@Import(RedisAutoConfiguration.class)
public class RedisConfig {
}
