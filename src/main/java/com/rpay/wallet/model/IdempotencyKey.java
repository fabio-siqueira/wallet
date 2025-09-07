package com.rpay.wallet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "idempotency_key", indexes = {
        @Index(name = "idx_user_operation", columnList = "userId, operation")
})
@EntityListeners(AuditingEntityListener.class)
public class IdempotencyKey {
    @Id
    @Column(name = "`key`", length = 100)
    private String key;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private String referenceId; // Pode ser o ID da transação

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}


