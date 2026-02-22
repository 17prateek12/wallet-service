package com.example.walletService.idempotency;

/**
 * Cache for idempotency keys to support fast lookup and parallel-safe claiming
 * in distributed setups. Check cache first (faster than DB); on miss, claim then
 * process and store result.
 */
public interface IdempotencyCache {

    String PREFIX = "idempotency:";
    String CLAIM_VALUE = "processing";
    int CLAIM_TTL_SECONDS = 120;
    int SUCCESS_TTL_SECONDS = 86400; // 24 hours

    /**
     * Returns the stored transaction id for this key if the operation was already completed.
     *
     * @param key idempotency key
     * @return transaction id, or null if not found / not yet completed
     */
    Long get(String key);

    /**
     * Tries to claim the key for processing (e.g. Redis SET NX). Only one caller should get true.
     *
     * @param key idempotency key
     * @return true if this caller claimed the key and should process; false if another has claimed or already completed
     */
    boolean tryClaim(String key);

    /**
     * Stores the transaction id after successful processing.
     *
     * @param key   idempotency key
     * @param txId  transaction id
     * @param ttlSeconds TTL for the key (e.g. 24h)
     */
    void put(String key, long txId, int ttlSeconds);

    /**
     * Removes the key so the client can retry (e.g. after processing failed).
     *
     * @param key idempotency key
     */
    void delete(String key);

    /**
     * Whether this implementation actually uses a backing store (e.g. Redis).
     * If false, idempotency is handled only via DB.
     */
    boolean isActive();
}
