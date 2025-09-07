package com.rpay.wallet.service;

import com.rpay.wallet.dto.TransactionRequest;
import com.rpay.wallet.dto.TransferRequest;
import com.rpay.wallet.exception.ConflictException;
import com.rpay.wallet.exception.NotFoundException;
import com.rpay.wallet.model.Transaction;
import com.rpay.wallet.model.Wallet;
import com.rpay.wallet.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;

    public WalletService(WalletRepository walletRepository,
                        TransactionService transactionService,
                        IdempotencyService idempotencyService) {
        this.walletRepository = walletRepository;
        this.transactionService = transactionService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Cria uma nova carteira para o usuário
     */
    public Wallet createWallet(String userId) {
        try {
            return walletRepository.save(new Wallet(userId));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Wallet already exists for user: " + userId);
        }
    }

    /**
     * Retorna o saldo atual da carteira
     */
    public BigDecimal getBalance(String userId) {
        Wallet wallet = getWallet(userId);
        return wallet.getBalance();
    }

    /**
     * Retorna o saldo histórico da carteira em um timestamp específico
     */
    public BigDecimal getHistoricalBalance(String userId, LocalDateTime timestamp) {
        return transactionService.getHistoricalBalance(userId, timestamp);
    }

    /**
     * Executa um depósito com controle de idempotência
     */
    @Transactional
    public Transaction deposit(TransactionRequest request, String idempotencyKey) {
        return idempotencyService.executeWithIdempotency(
            idempotencyKey,
            request.getUserId(),
            "DEPOSIT",
            () -> transactionService.performDeposit(request)
        );
    }

    /**
     * Executa um saque com controle de idempotência
     */
    @Transactional
    public Transaction withdraw(TransactionRequest request, String idempotencyKey) {
        return idempotencyService.executeWithIdempotency(
            idempotencyKey,
            request.getUserId(),
            "WITHDRAWAL",
            () -> transactionService.performWithdraw(request)
        );
    }

    /**
     * Executa uma transferência com controle de idempotência
     */
    @Transactional
    public Transaction transfer(TransferRequest request, String idempotencyKey) {
        return idempotencyService.executeWithIdempotency(
            idempotencyKey,
            request.getFromUserId(),
            "TRANSFER_OUT",
            () -> transactionService.performTransfer(request)
        );
    }

    private Wallet getWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + userId));
    }
}
