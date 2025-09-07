package com.rpay.wallet.repository;

import com.rpay.wallet.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByFromUserIdOrderByCreatedAtDesc(String userId);

    @Query("SELECT t FROM Transaction t WHERE t.fromUserId = :userId AND t.createdAt <= :timestamp ORDER BY t.createdAt DESC")
    List<Transaction> findByFromUserIdAndCreatedAtBeforeOrderByCreatedAtDesc(String userId, LocalDateTime timestamp);

    @Query("SELECT t FROM Transaction t WHERE t.fromUserId = :userId AND t.createdAt <= :timestamp ORDER BY t.createdAt DESC LIMIT 1")
    Optional<Transaction> findLastTransactionBeforeTimestamp(String userId, LocalDateTime timestamp);
}