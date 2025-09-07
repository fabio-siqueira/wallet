package com.rpay.wallet.service;

import com.rpay.wallet.exception.ConflictException;
import com.rpay.wallet.model.IdempotencyKey;
import com.rpay.wallet.model.Transaction;
import com.rpay.wallet.repository.IdempotencyKeyRepository;
import com.rpay.wallet.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void executeWithIdempotency_shouldExecuteOperation_whenNoIdempotencyKey() {
        // Arrange
        Transaction expectedTransaction = new Transaction();
        expectedTransaction.setId(UUID.randomUUID());
        Supplier<Transaction> supplier = () -> expectedTransaction;

        // Act
        Transaction result = idempotencyService.executeWithIdempotency(null, "user123", "DEPOSIT", supplier);

        // Assert
        assertEquals(expectedTransaction, result);
        verify(idempotencyKeyRepository, never()).findByKeyAndUserIdAndOperation(any(), any(), any());
        verify(idempotencyKeyRepository, never()).save(any());
    }

    @Test
    void executeWithIdempotency_shouldExecuteAndSaveRecord_whenNewIdempotencyKey() {
        // Arrange
        String idempotencyKey = "test-key-123";
        String userId = "user123";
        String operation = "DEPOSIT";

        Transaction expectedTransaction = new Transaction();
        expectedTransaction.setId(UUID.randomUUID());
        Supplier<Transaction> supplier = () -> expectedTransaction;

        when(idempotencyKeyRepository.findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation))
                .thenReturn(Optional.empty());

        // Act
        Transaction result = idempotencyService.executeWithIdempotency(idempotencyKey, userId, operation, supplier);

        // Assert
        assertEquals(expectedTransaction, result);
        verify(idempotencyKeyRepository).findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation);
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
    }

    @Test
    void executeWithIdempotency_shouldReturnExistingTransaction_whenIdempotencyKeyExists() {
        // Arrange
        String idempotencyKey = "test-key-123";
        String userId = "user123";
        String operation = "DEPOSIT";
        UUID transactionId = UUID.randomUUID();

        IdempotencyKey existingRecord = new IdempotencyKey();
        existingRecord.setReferenceId(transactionId.toString());

        Transaction existingTransaction = new Transaction();
        existingTransaction.setId(transactionId);

        Supplier<Transaction> supplier = mock(Supplier.class);

        when(idempotencyKeyRepository.findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation))
                .thenReturn(Optional.of(existingRecord));
        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(existingTransaction));

        // Act
        Transaction result = idempotencyService.executeWithIdempotency(idempotencyKey, userId, operation, supplier);

        // Assert
        assertEquals(existingTransaction, result);
        verify(supplier, never()).get(); // Não deve executar a operação
        verify(idempotencyKeyRepository, never()).save(any()); // Não deve salvar novo registro
    }

    @Test
    void executeWithIdempotency_shouldThrowConflictException_whenIdempotencyKeyExistsButTransactionNotFound() {
        // Arrange
        String idempotencyKey = "test-key-123";
        String userId = "user123";
        String operation = "DEPOSIT";
        UUID transactionId = UUID.randomUUID();

        IdempotencyKey existingRecord = new IdempotencyKey();
        existingRecord.setReferenceId(transactionId.toString());

        Supplier<Transaction> supplier = mock(Supplier.class);

        when(idempotencyKeyRepository.findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation))
                .thenReturn(Optional.of(existingRecord));
        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.empty());

        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class,
                () -> idempotencyService.executeWithIdempotency(idempotencyKey, userId, operation, supplier));

        assertEquals("Transação já processada para este idempotency key", exception.getMessage());
        verify(supplier, never()).get();
    }

    @Test
    void exists_shouldReturnTrue_whenIdempotencyKeyExists() {
        // Arrange
        String idempotencyKey = "test-key-123";
        String userId = "user123";
        String operation = "DEPOSIT";

        when(idempotencyKeyRepository.findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation))
                .thenReturn(Optional.of(new IdempotencyKey()));

        // Act
        boolean result = idempotencyService.exists(idempotencyKey, userId, operation);

        // Assert
        assertTrue(result);
    }

    @Test
    void exists_shouldReturnFalse_whenIdempotencyKeyDoesNotExist() {
        // Arrange
        String idempotencyKey = "test-key-123";
        String userId = "user123";
        String operation = "DEPOSIT";

        when(idempotencyKeyRepository.findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation))
                .thenReturn(Optional.empty());

        // Act
        boolean result = idempotencyService.exists(idempotencyKey, userId, operation);

        // Assert
        assertFalse(result);
    }

    @Test
    void findExistingTransaction_shouldReturnTransaction_whenIdempotencyKeyAndTransactionExist() {
        // Arrange
        String idempotencyKey = "test-key-123";
        String userId = "user123";
        String operation = "DEPOSIT";
        UUID transactionId = UUID.randomUUID();

        IdempotencyKey existingRecord = new IdempotencyKey();
        existingRecord.setReferenceId(transactionId.toString());

        Transaction existingTransaction = new Transaction();
        existingTransaction.setId(transactionId);

        when(idempotencyKeyRepository.findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation))
                .thenReturn(Optional.of(existingRecord));
        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.of(existingTransaction));

        // Act
        Optional<Transaction> result = idempotencyService.findExistingTransaction(idempotencyKey, userId, operation);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(existingTransaction, result.get());
    }

    @Test
    void findExistingTransaction_shouldReturnEmpty_whenIdempotencyKeyDoesNotExist() {
        // Arrange
        String idempotencyKey = "test-key-123";
        String userId = "user123";
        String operation = "DEPOSIT";

        when(idempotencyKeyRepository.findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation))
                .thenReturn(Optional.empty());

        // Act
        Optional<Transaction> result = idempotencyService.findExistingTransaction(idempotencyKey, userId, operation);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void findExistingTransaction_shouldReturnEmpty_whenIdempotencyKeyExistsButTransactionDoesNot() {
        // Arrange
        String idempotencyKey = "test-key-123";
        String userId = "user123";
        String operation = "DEPOSIT";
        UUID transactionId = UUID.randomUUID();

        IdempotencyKey existingRecord = new IdempotencyKey();
        existingRecord.setReferenceId(transactionId.toString());

        when(idempotencyKeyRepository.findByKeyAndUserIdAndOperation(idempotencyKey, userId, operation))
                .thenReturn(Optional.of(existingRecord));
        when(transactionRepository.findById(transactionId))
                .thenReturn(Optional.empty());

        // Act
        Optional<Transaction> result = idempotencyService.findExistingTransaction(idempotencyKey, userId, operation);

        // Assert
        assertFalse(result.isPresent());
    }
}
