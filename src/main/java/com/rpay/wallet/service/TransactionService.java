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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    public TransactionService(TransactionRepository transactionRepository,
                             WalletRepository walletRepository) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
    }

    /**
     * Busca o histórico de saldo em um timestamp específico
     */
    public BigDecimal getHistoricalBalance(String userId, LocalDateTime timestamp) {
        Optional<Transaction> lastTransaction = transactionRepository
                .findLastTransactionBeforeTimestamp(userId, timestamp);

        if (lastTransaction.isPresent()) {
            return lastTransaction.get().getBalanceAfter();
        } else {
            // If no transactions before timestamp, check if wallet existed
            Wallet wallet = findWallet(userId);

            if (wallet.getCreatedAt().isAfter(timestamp)) {
                throw new UnprocessableEntityException("Wallet did not exist at the specified time");
            }
            return BigDecimal.ZERO;
        }
    }

    /**
     * Executa um depósito
     */
    public Transaction performDeposit(TransactionRequest request) {
        Wallet wallet = walletRepository.findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + request.getUserId()));

        BigDecimal newBalance = wallet.getBalance().add(request.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .fromUserId(request.getUserId())
                .type(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .balanceAfter(newBalance)
                .description(request.getDescription())
                .build();

        return transactionRepository.save(transaction);
    }

    /**
     * Executa um saque
     */
    public Transaction performWithdraw(TransactionRequest request) {
        Wallet wallet = walletRepository.findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + request.getUserId()));

        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new UnprocessableEntityException("Insufficient funds");
        }

        BigDecimal newBalance = wallet.getBalance().subtract(request.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .fromUserId(request.getUserId())
                .type(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .balanceAfter(newBalance)
                .description(request.getDescription())
                .build();

        return transactionRepository.save(transaction);
    }

    /**
     * Executa uma transferência
     */
    public Transaction performTransfer(TransferRequest request) {
        if (request.getFromUserId().equals(request.getToUserId())) {
            throw new UnprocessableEntityException("Cannot transfer to the same wallet");
        }

        // Lock wallets in a consistent order to prevent deadlock
        String firstUserId = request.getFromUserId().compareTo(request.getToUserId()) < 0
                ? request.getFromUserId() : request.getToUserId();
        String secondUserId = request.getFromUserId().compareTo(request.getToUserId()) < 0
                ? request.getToUserId() : request.getFromUserId();

        Wallet firstWallet = walletRepository.findByUserIdWithLock(firstUserId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + firstUserId));
        Wallet secondWallet = walletRepository.findByUserIdWithLock(secondUserId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + secondUserId));

        Wallet fromWallet = request.getFromUserId().equals(firstUserId) ? firstWallet : secondWallet;
        Wallet toWallet = request.getFromUserId().equals(firstUserId) ? secondWallet : firstWallet;

        if (fromWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new UnprocessableEntityException("Insufficient funds");
        }

        // Update balances
        BigDecimal fromNewBalance = fromWallet.getBalance().subtract(request.getAmount());
        BigDecimal toNewBalance = toWallet.getBalance().add(request.getAmount());

        fromWallet.setBalance(fromNewBalance);
        toWallet.setBalance(toNewBalance);

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        // Create transaction records
        Transaction outTransaction = Transaction.builder()
                .description(request.getDescription())
                .fromUserId(request.getFromUserId())
                .toUserId(request.getToUserId())
                .type(TransactionType.TRANSFER_OUT)
                .amount(request.getAmount())
                .balanceAfter(fromNewBalance)
                .build();
        outTransaction = transactionRepository.save(outTransaction);

        Transaction inTransaction = Transaction.builder()
                .description(request.getDescription())
                .fromUserId(request.getToUserId())
                .toUserId(request.getFromUserId())
                .type(TransactionType.TRANSFER_IN)
                .amount(request.getAmount())
                .balanceAfter(toNewBalance)
                .build();
        transactionRepository.save(inTransaction);

        return outTransaction;
    }

    private Wallet findWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));
    }
}
