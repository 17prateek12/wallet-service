package com.example.walletService.cache;

/**
 * Optional cache for wallet balance to reduce DB reads on GET /wallet/{userId}/balance.
 * When Redis is enabled, use cache-aside: read from cache on GET; on miss load from DB and cache.
 * Invalidate (delete) on any write (topup/bonus/spend) for that wallet.
 */
public interface BalanceCache {

    String KEY_PREFIX = "balance:wallet:";
    int TTL_SECONDS = 300; // 5 min safety; invalidation on write keeps correctness

    /**
     * Returns cached balance for the wallet, or null if miss or cache disabled.
     */
    Long get(Long walletId);

    /**
     * Stores balance for the wallet (e.g. after loading from DB).
     */
    void put(Long walletId, long balance);

    /**
     * Invalidates cache for the wallet (call after any topup/bonus/spend touching this wallet).
     */
    void invalidate(Long walletId);

    /**
     * Whether this implementation uses a backing store (e.g. Redis).
     */
    boolean isActive();
}
