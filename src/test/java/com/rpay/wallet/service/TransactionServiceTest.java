package com.rpay.wallet.service;

import com.rpay.wallet.dto.TransactionRequest;
import com.rpay.wallet.dto.TransferRequest;
import com.rpay.wallet.exception.NotFoundException;
import com.rpay.wallet.exception.UnprocessableEntityException;
import com.rpay.wallet.model.Transaction;
import com.rpay.wallet.model.TransactionType;
import com.rpay.wallet.model.Wallet;
import com.rpay.wallet.repository.TransactionRepository;
import com.rpay.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void getHistoricalBalance_shouldReturnBalanceFromLastTransaction_whenTransactionExists() {
        // Arrange
        String userId = "user123";
        LocalDateTime timestamp = LocalDateTime.now().minusDays(1);
        BigDecimal expectedBalance = new BigDecimal("75.25");

        Transaction lastTransaction = new Transaction();
        lastTransaction.setBalanceAfter(expectedBalance);
        when(transactionRepository.findLastTransactionBeforeTimestamp(userId, timestamp))
                .thenReturn(Optional.of(lastTransaction));

        // Act
        BigDecimal result = transactionService.getHistoricalBalance(userId, timestamp);

        // Assert
        assertEquals(expectedBalance, result);
    }

    @Test
    void getHistoricalBalance_shouldReturnZero_whenNoTransactionsBeforeTimestamp() {
        // Arrange
        String userId = "user123";
        LocalDateTime timestamp = LocalDateTime.now().minusDays(1);

        Wallet wallet = new Wallet(userId);
        wallet.setCreatedAt(LocalDateTime.now().minusDays(2));

        when(transactionRepository.findLastTransactionBeforeTimestamp(userId, timestamp))
                .thenReturn(Optional.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        // Act
        BigDecimal result = transactionService.getHistoricalBalance(userId, timestamp);

        // Assert
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void getHistoricalBalance_shouldThrowException_whenWalletDidNotExistAtTimestamp() {
        // Arrange
        String userId = "user123";
        LocalDateTime timestamp = LocalDateTime.now().minusDays(5);

        Wallet wallet = new Wallet(userId);
        wallet.setCreatedAt(LocalDateTime.now().minusDays(1));

        when(transactionRepository.findLastTransactionBeforeTimestamp(userId, timestamp))
                .thenReturn(Optional.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        // Act & Assert
        UnprocessableEntityException exception = assertThrows(UnprocessableEntityException.class,
                () -> transactionService.getHistoricalBalance(userId, timestamp));

        assertEquals("Wallet did not exist at the specified time", exception.getMessage());
    }

    @Test
    void performDeposit_shouldIncreaseBalanceAndCreateTransaction() {
        // Arrange
        String userId = "user123";
        BigDecimal initialBalance = new BigDecimal("50.00");
        BigDecimal depositAmount = new BigDecimal("25.00");
        BigDecimal expectedBalance = new BigDecimal("75.00");

        TransactionRequest request = new TransactionRequest();
        request.setUserId(userId);
        request.setAmount(depositAmount);
        request.setDescription("Test deposit");

        Wallet wallet = new Wallet(userId);
        wallet.setBalance(initialBalance);

        Transaction savedTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .fromUserId(userId)
                .type(TransactionType.DEPOSIT)
                .amount(depositAmount)
                .balanceAfter(expectedBalance)
                .description("Test deposit")
                .build();

        when(walletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);

        // Act
        Transaction result = transactionService.performDeposit(request);

        // Assert
        assertEquals(expectedBalance, wallet.getBalance());
        assertEquals(savedTransaction, result);
        verify(walletRepository).save(wallet);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void performDeposit_shouldThrowException_whenWalletNotFound() {
        // Arrange
        String userId = "nonexistent";
        TransactionRequest request = new TransactionRequest();
        request.setUserId(userId);
        request.setAmount(new BigDecimal("25.00"));

        when(walletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.empty());

        // Act & Assert
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> transactionService.performDeposit(request));

        assertEquals("Wallet not found for user: " + userId, exception.getMessage());
    }

    @Test
    void performWithdraw_shouldDecreaseBalanceAndCreateTransaction_whenSufficientFunds() {
        // Arrange
        String userId = "user123";
        BigDecimal initialBalance = new BigDecimal("100.00");
        BigDecimal withdrawAmount = new BigDecimal("30.00");
        BigDecimal expectedBalance = new BigDecimal("70.00");

        TransactionRequest request = new TransactionRequest();
        request.setUserId(userId);
        request.setAmount(withdrawAmount);
        request.setDescription("Test withdrawal");

        Wallet wallet = new Wallet(userId);
        wallet.setBalance(initialBalance);

        Transaction savedTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .fromUserId(userId)
                .type(TransactionType.WITHDRAWAL)
                .amount(withdrawAmount)
                .balanceAfter(expectedBalance)
                .description("Test withdrawal")
                .build();

        when(walletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);

        // Act
        Transaction result = transactionService.performWithdraw(request);

        // Assert
        assertEquals(expectedBalance, wallet.getBalance());
        assertEquals(savedTransaction, result);
        verify(walletRepository).save(wallet);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void performWithdraw_shouldThrowException_whenInsufficientFunds() {
        // Arrange
        String userId = "user123";
        BigDecimal initialBalance = new BigDecimal("20.00");
        BigDecimal withdrawAmount = new BigDecimal("30.00");

        TransactionRequest request = new TransactionRequest();
        request.setUserId(userId);
        request.setAmount(withdrawAmount);

        Wallet wallet = new Wallet(userId);
        wallet.setBalance(initialBalance);

        when(walletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(wallet));

        // Act & Assert
        UnprocessableEntityException exception = assertThrows(UnprocessableEntityException.class,
                () -> transactionService.performWithdraw(request));

        assertEquals("Insufficient funds", exception.getMessage());
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void performTransfer_shouldMoveFundsBetweenWallets_whenValidRequest() {
        // Arrange
        String fromUserId = "user1";
        String toUserId = "user2";
        BigDecimal transferAmount = new BigDecimal("50.00");
        BigDecimal fromInitialBalance = new BigDecimal("100.00");
        BigDecimal toInitialBalance = new BigDecimal("25.00");

        TransferRequest request = new TransferRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setAmount(transferAmount);
        request.setDescription("Test transfer");

        Wallet fromWallet = new Wallet(fromUserId);
        fromWallet.setBalance(fromInitialBalance);
        Wallet toWallet = new Wallet(toUserId);
        toWallet.setBalance(toInitialBalance);

        Transaction outTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .type(TransactionType.TRANSFER_OUT)
                .amount(transferAmount)
                .balanceAfter(new BigDecimal("50.00"))
                .description("Test transfer")
                .build();

        when(walletRepository.findByUserIdWithLock(fromUserId)).thenReturn(Optional.of(fromWallet));
        when(walletRepository.findByUserIdWithLock(toUserId)).thenReturn(Optional.of(toWallet));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(outTransaction);

        // Act
        Transaction result = transactionService.performTransfer(request);

        // Assert
        assertEquals(new BigDecimal("50.00"), fromWallet.getBalance());
        assertEquals(new BigDecimal("75.00"), toWallet.getBalance());
        assertEquals(outTransaction, result);
        verify(walletRepository, times(2)).save(any(Wallet.class));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void performTransfer_shouldThrowException_whenSameWallet() {
        // Arrange
        String userId = "user1";
        TransferRequest request = new TransferRequest();
        request.setFromUserId(userId);
        request.setToUserId(userId);
        request.setAmount(new BigDecimal("50.00"));

        // Act & Assert
        UnprocessableEntityException exception = assertThrows(UnprocessableEntityException.class,
                () -> transactionService.performTransfer(request));

        assertEquals("Cannot transfer to the same wallet", exception.getMessage());
        verify(walletRepository, never()).findByUserIdWithLock(any());
    }

    @Test
    void performTransfer_shouldThrowException_whenInsufficientFunds() {
        // Arrange
        String fromUserId = "user1";
        String toUserId = "user2";
        BigDecimal transferAmount = new BigDecimal("150.00");
        BigDecimal fromInitialBalance = new BigDecimal("100.00");

        TransferRequest request = new TransferRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setAmount(transferAmount);

        Wallet fromWallet = new Wallet(fromUserId);
        fromWallet.setBalance(fromInitialBalance);
        Wallet toWallet = new Wallet(toUserId);

        when(walletRepository.findByUserIdWithLock(fromUserId)).thenReturn(Optional.of(fromWallet));
        when(walletRepository.findByUserIdWithLock(toUserId)).thenReturn(Optional.of(toWallet));

        // Act & Assert
        UnprocessableEntityException exception = assertThrows(UnprocessableEntityException.class,
                () -> transactionService.performTransfer(request));

        assertEquals("Insufficient funds", exception.getMessage());
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void performTransfer_shouldThrowException_whenFromWalletNotFound() {
        // Arrange
        String fromUserId = "nonexistent";
        String toUserId = "user2";
        TransferRequest request = new TransferRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setAmount(new BigDecimal("50.00"));

        when(walletRepository.findByUserIdWithLock(fromUserId)).thenReturn(Optional.empty());

        // Act & Assert
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> transactionService.performTransfer(request));

        assertEquals("Wallet not found for user: " + fromUserId, exception.getMessage());
    }

    @Test
    void performTransfer_shouldThrowException_whenToWalletNotFound() {
        // Arrange
        String fromUserId = "user1";
        String toUserId = "nonexistent";
        TransferRequest request = new TransferRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setAmount(new BigDecimal("50.00"));

        when(walletRepository.findByUserIdWithLock(toUserId)).thenReturn(Optional.empty());

        // Act & Assert
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> transactionService.performTransfer(request));

        assertEquals("Wallet not found for user: " + toUserId, exception.getMessage());
    }
}
