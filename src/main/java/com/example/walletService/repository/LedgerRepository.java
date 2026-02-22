package com.example.walletService.repository;

import com.example.walletService.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l WHERE l.walletId = :walletId")
    Long getBalance(Long walletId);

    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(Long walletId);
}
