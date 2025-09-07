package com.rpay.wallet.repository;

import com.rpay.wallet.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
    Optional<IdempotencyKey> findByKeyAndUserIdAndOperation(String key, String userId, String operation);
}

