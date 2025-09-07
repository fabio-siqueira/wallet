package com.rpay.wallet.service;

import com.rpay.wallet.dto.TransactionRequest;
import com.rpay.wallet.dto.TransferRequest;
import com.rpay.wallet.exception.ConflictException;
import com.rpay.wallet.exception.NotFoundException;
import com.rpay.wallet.model.Transaction;
import com.rpay.wallet.model.Wallet;
import com.rpay.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private WalletService walletService;

    @Test
    void createWallet_shouldReturnNewWallet_whenUserWalletDoesNotExist() {
        String userId = "user123";

        Wallet expectedWallet = new Wallet(userId);
        when(walletRepository.save(any(Wallet.class))).thenReturn(expectedWallet);

        Wallet result = walletService.createWallet(userId);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void createWallet_shouldThrowException_whenWalletAlreadyExists() {
        String userId = "user123";
        when(walletRepository.save(any(Wallet.class))).thenThrow(new DataIntegrityViolationException("constraint violation"));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> walletService.createWallet(userId));

        assertEquals("Wallet already exists for user: " + userId, exception.getMessage());
    }

    @Test
    void getBalance_shouldReturnCurrentBalance_whenWalletExists() {
        String userId = "user123";
        BigDecimal expectedBalance = new BigDecimal("100.50");
        Wallet wallet = new Wallet(userId);
        wallet.setBalance(expectedBalance);
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        BigDecimal result = walletService.getBalance(userId);

        assertEquals(expectedBalance, result);
    }

    @Test
    void getBalance_shouldThrowException_whenWalletDoesNotExist() {
        String userId = "nonexistent";
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> walletService.getBalance(userId));

        assertEquals("Wallet not found for user: " + userId, exception.getMessage());
    }

    @Test
    void getHistoricalBalance_shouldDelegateToTransactionService() {
        String userId = "user123";
        LocalDateTime timestamp = LocalDateTime.now().minusDays(1);
        BigDecimal expectedBalance = new BigDecimal("75.25");

        when(transactionService.getHistoricalBalance(userId, timestamp))
                .thenReturn(expectedBalance);

        BigDecimal result = walletService.getHistoricalBalance(userId, timestamp);

        assertEquals(expectedBalance, result);
        verify(transactionService).getHistoricalBalance(userId, timestamp);
    }

    @Test
    void deposit_shouldDelegateToIdempotencyService() {
        String userId = "user123";
        String idempotencyKey = "deposit-123e4567-e89b-12d3-a456-426614174000";
        TransactionRequest request = new TransactionRequest();
        request.setUserId(userId);
        request.setAmount(new BigDecimal("100.00"));

        Transaction expectedTransaction = new Transaction();
        expectedTransaction.setId(UUID.randomUUID());
        when(idempotencyService.executeWithIdempotency(eq(idempotencyKey), eq(userId), eq("DEPOSIT"), any()))
                .thenReturn(expectedTransaction);

        Transaction result = walletService.deposit(request, idempotencyKey);

        assertEquals(expectedTransaction, result);
        verify(idempotencyService).executeWithIdempotency(eq(idempotencyKey), eq(userId), eq("DEPOSIT"), any());
    }

    @Test
    void deposit_shouldDelegateToIdempotencyService_whenNoIdempotencyKey() {
        String userId = "user123";
        TransactionRequest request = new TransactionRequest();
        request.setUserId(userId);
        request.setAmount(new BigDecimal("100.00"));

        Transaction expectedTransaction = new Transaction();
        expectedTransaction.setId(UUID.randomUUID());
        when(idempotencyService.executeWithIdempotency(eq(null), eq(userId), eq("DEPOSIT"), any()))
                .thenReturn(expectedTransaction);

        Transaction result = walletService.deposit(request, null);

        assertEquals(expectedTransaction, result);
        verify(idempotencyService).executeWithIdempotency(eq(null), eq(userId), eq("DEPOSIT"), any());
    }

    @Test
    void withdraw_shouldDelegateToIdempotencyService() {
        String userId = "user123";
        String idempotencyKey = "withdraw-987fcdeb-51a2-43d7-8b9e-123456789abc";
        TransactionRequest request = new TransactionRequest();
        request.setUserId(userId);
        request.setAmount(new BigDecimal("50.00"));

        Transaction expectedTransaction = new Transaction();
        expectedTransaction.setId(UUID.randomUUID());
        when(idempotencyService.executeWithIdempotency(eq(idempotencyKey), eq(userId), eq("WITHDRAWAL"), any()))
                .thenReturn(expectedTransaction);

        Transaction result = walletService.withdraw(request, idempotencyKey);

        assertEquals(expectedTransaction, result);
        verify(idempotencyService).executeWithIdempotency(eq(idempotencyKey), eq(userId), eq("WITHDRAWAL"), any());
    }

    @Test
    void withdraw_shouldDelegateToIdempotencyService_whenNoIdempotencyKey() {
        String userId = "user123";
        TransactionRequest request = new TransactionRequest();
        request.setUserId(userId);
        request.setAmount(new BigDecimal("50.00"));

        Transaction expectedTransaction = new Transaction();
        expectedTransaction.setId(UUID.randomUUID());
        when(idempotencyService.executeWithIdempotency(eq(null), eq(userId), eq("WITHDRAWAL"), any()))
                .thenReturn(expectedTransaction);

        Transaction result = walletService.withdraw(request, null);

        assertEquals(expectedTransaction, result);
        verify(idempotencyService).executeWithIdempotency(eq(null), eq(userId), eq("WITHDRAWAL"), any());
    }

    @Test
    void transfer_shouldDelegateToIdempotencyService() {
        String fromUserId = "user1";
        String toUserId = "user2";
        String idempotencyKey = "transfer-456789ab-cdef-1234-5678-90abcdef1234";
        TransferRequest request = new TransferRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setAmount(new BigDecimal("75.00"));

        Transaction expectedTransaction = new Transaction();
        expectedTransaction.setId(UUID.randomUUID());
        when(idempotencyService.executeWithIdempotency(eq(idempotencyKey), eq(fromUserId), eq("TRANSFER_OUT"), any()))
                .thenReturn(expectedTransaction);

        Transaction result = walletService.transfer(request, idempotencyKey);

        assertEquals(expectedTransaction, result);
        verify(idempotencyService).executeWithIdempotency(eq(idempotencyKey), eq(fromUserId), eq("TRANSFER_OUT"), any());
    }

    @Test
    void transfer_shouldDelegateToIdempotencyService_whenNoIdempotencyKey() {
        String fromUserId = "user1";
        String toUserId = "user2";
        TransferRequest request = new TransferRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setAmount(new BigDecimal("75.00"));

        Transaction expectedTransaction = new Transaction();
        expectedTransaction.setId(UUID.randomUUID());
        when(idempotencyService.executeWithIdempotency(eq(null), eq(fromUserId), eq("TRANSFER_OUT"), any()))
                .thenReturn(expectedTransaction);

        Transaction result = walletService.transfer(request, null);

        assertEquals(expectedTransaction, result);
        verify(idempotencyService).executeWithIdempotency(eq(null), eq(fromUserId), eq("TRANSFER_OUT"), any());
    }
}
