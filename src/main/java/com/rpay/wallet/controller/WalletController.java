package com.rpay.wallet.controller;

import com.rpay.wallet.dto.BalanceResponse;
import com.rpay.wallet.dto.CreateWalletRequest;
import com.rpay.wallet.dto.TransactionRequest;
import com.rpay.wallet.dto.TransferRequest;
import com.rpay.wallet.model.Transaction;
import com.rpay.wallet.model.Wallet;
import com.rpay.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public Wallet createWallet(@Valid @RequestBody CreateWalletRequest request) {
        return walletService.createWallet(request.getUserId());
    }

    @ResponseStatus(OK)
    @GetMapping("/{userId}/balance")
    public BalanceResponse getBalance(@PathVariable String userId) {
        BigDecimal balance = walletService.getBalance(userId);
        return new BalanceResponse(balance);
    }

    @ResponseStatus(OK)
    @GetMapping("/{userId}/balance/historical")
    public BalanceResponse getHistoricalBalance(
            @PathVariable String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timestamp) {
        BigDecimal balance = walletService.getHistoricalBalance(userId, timestamp);
        return new BalanceResponse(balance);
    }

    @ResponseStatus(CREATED)
    @PostMapping("/deposit")
    public Transaction deposit(@Valid @RequestBody TransactionRequest request,
                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return walletService.deposit(request, idempotencyKey);
    }

    @ResponseStatus(CREATED)
    @PostMapping("/withdraw")
    public Transaction withdraw(@Valid @RequestBody TransactionRequest request,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return walletService.withdraw(request, idempotencyKey);
    }

    @ResponseStatus(CREATED)
    @PostMapping("/transfer")
    public Transaction transfer(@Valid @RequestBody TransferRequest request,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return walletService.transfer(request, idempotencyKey);
    }
}
