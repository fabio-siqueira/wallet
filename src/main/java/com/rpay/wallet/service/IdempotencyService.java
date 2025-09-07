package com.rpay.wallet.service;

import com.rpay.wallet.exception.ConflictException;
import com.rpay.wallet.model.IdempotencyKey;
import com.rpay.wallet.model.Transaction;
import com.rpay.wallet.repository.IdempotencyKeyRepository;
import com.rpay.wallet.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionRepository transactionRepository;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository,
                              TransactionRepository transactionRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Executa uma operação com controle de idempotência
     *
     * @param idempotencyKey      Chave de idempotência (pode ser null)
     * @param userId              ID do usuário
     * @param operation           Tipo da operação (DEPOSIT, WITHDRAWAL, TRANSFER_OUT)
     * @param transactionSupplier Função que executa a operação e retorna a transação
     * @return A transação (nova ou existente)
     */
    public Transaction executeWithIdempotency(String idempotencyKey,
                                              String userId,
                                              String operation,
                                              Supplier<Transaction> transactionSupplier) {
        // Se não há idempotency key, executa normalmente
        if (idempotencyKey == null) {
            return transactionSupplier.get();
        }

        // Verifica se já existe
        Optional<IdempotencyKey> existing = idempotencyKeyRepository
                .findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation);

        if (existing.isPresent()) {
            // Retorna a transação existente
            return transactionRepository.findById(UUID.fromString(existing.get().getReferenceId()))
                    .orElseThrow(() -> new ConflictException("Transação já processada para este idempotency key"));
        }

        // Executa a operação
        Transaction transaction = transactionSupplier.get();

        // Salva o registro de idempotência
        saveIdempotencyRecord(idempotencyKey, userId, operation, transaction.getId().toString());

        return transaction;
    }

    /**
     * Salva um registro de idempotência
     */
    private void saveIdempotencyRecord(String idempotencyKey, String userId, String operation, String transactionId) {
        idempotencyKeyRepository.save(IdempotencyKey.builder()
                .referenceId(transactionId)
                .operation(operation)
                .key(idempotencyKey)
                .userId(userId)
                .build());
    }

    /**
     * Verifica se uma chave de idempotência já existe
     */
    public boolean exists(String idempotencyKey, String userId, String operation) {
        return idempotencyKeyRepository.findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation)
                .isPresent();
    }

    /**
     * Busca uma transação existente por idempotency key
     */
    public Optional<Transaction> findExistingTransaction(String idempotencyKey, String userId, String operation) {
        return idempotencyKeyRepository.findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation)
                .flatMap(record -> transactionRepository.findById(UUID.fromString(record.getReferenceId())));
    }
}
