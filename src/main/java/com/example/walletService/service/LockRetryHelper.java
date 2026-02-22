package com.example.walletService.service;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;

import jakarta.persistence.PessimisticLockException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Detects lock timeout / deadlock and computes backoff for retries.
 */
public final class LockRetryHelper {

    private LockRetryHelper() {}

    /** Returns true if the exception indicates lock timeout or deadlock (retryable). */
    public static boolean isRetryable(Throwable t) {
        if (t == null) return false;
        if (t instanceof PessimisticLockException
                || t instanceof CannotAcquireLockException
                || t instanceof DeadlockLoserDataAccessException) {
            return true;
        }
        String msg = t.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("deadlock") || lower.contains("lock timeout")
                    || lower.contains("could not obtain lock") || lower.contains("lock wait timeout")
                    || lower.contains("canceling statement due to lock timeout")) {
                return true;
            }
        }
        return isRetryable(t.getCause());
    }

    /** Exponential backoff with jitter: baseMs * 2^attempt + random 0..baseMs. */
    public static long backoffMs(int attempt, int baseBackoffMs) {
        long delay = (long) baseBackoffMs * (1L << Math.min(attempt, 10));
        long jitter = ThreadLocalRandom.current().nextLong(0, baseBackoffMs + 1);
        return delay + jitter;
    }
}
