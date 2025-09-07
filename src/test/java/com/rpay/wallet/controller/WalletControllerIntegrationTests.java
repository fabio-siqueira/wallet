package com.rpay.wallet.controller;

import com.rpay.wallet.ApplicationTests;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class WalletControllerIntegrationTests extends ApplicationTests {

    @Test
    void createWallet_shouldCreateWalletSuccessfully() throws Exception {

        ResultActions resultActions = mockMvc.perform(post("/api/wallets")
                .contentType(APPLICATION_JSON)
                .content("{\"userId\":\"user123\"}"));


        resultActions.andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void getBalance_shouldReturnCurrentBalance_whenWalletExists() throws Exception {
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user456\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user456\",\"amount\":100.50,\"description\":\"Initial deposit\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/wallets/user456/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.50));
    }

    @Test
    void getBalance_shouldReturnNotFound_whenWalletDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/wallets/nonexistent/balance"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistoricalBalance_shouldReturnBalanceAtSpecificTime() throws Exception {
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user789\"}"))
                .andExpect(status().isCreated());

        LocalDateTime pastTime = LocalDateTime.now().plusMinutes(1);
        String timestamp = pastTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        ResultActions resultActions = mockMvc.perform(get("/api/wallets/user789/balance/historical")
                .param("timestamp", timestamp));

        resultActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void getHistoricalBalance_shouldReturnUnprocessableEntity_whenWalletNotExistAtTheSpecifiedTime() throws Exception {
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user789\"}"))
                .andExpect(status().isCreated());

        LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
        String timestamp = pastTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        ResultActions resultActions = mockMvc.perform(get("/api/wallets/user789/balance/historical")
                .param("timestamp", timestamp));

        resultActions.andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Wallet did not exist at the specified time"))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").isNotEmpty());
    }

    @Test
    void deposit_shouldAddFundsToWallet() throws Exception {
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user101\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user101\",\"amount\":250.75,\"description\":\"Test deposit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(250.75))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.description").value("Test deposit"))
                .andExpect(jsonPath("$.balanceAfter").value(250.75));
    }

    @Test
    void deposit_shouldReturnNotFound_whenWalletDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"nonexistent\",\"amount\":100.00,\"description\":\"Test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Wallet not found for user: nonexistent"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").isNotEmpty());
    }

    @Test
    void withdraw_shouldDeductFundsFromWallet() throws Exception {
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user202\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user202\",\"amount\":500.00,\"description\":\"Initial funds\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/withdraw")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user202\",\"amount\":150.25,\"description\":\"Test withdrawal\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(150.25))
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.description").value("Test withdrawal"))
                .andExpect(jsonPath("$.balanceAfter").value(349.75));
    }

    @Test
    void withdraw_shouldReturnUnprocessableEntity_whenInsufficientFunds() throws Exception {
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user303\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/withdraw")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user303\",\"amount\":100.00,\"description\":\"Insufficient funds test\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Insufficient funds"))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").isNotEmpty());
    }

    @Test
    void transfer_shouldMoveFundsBetweenWallets() throws Exception {
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"sender404\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"receiver404\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"sender404\",\"amount\":1000.00,\"description\":\"Initial funds\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/transfer")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fromUserId\":\"sender404\",\"toUserId\":\"receiver404\",\"amount\":300.00,\"description\":\"Test transfer\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/wallets/sender404/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(700.00));

        mockMvc.perform(get("/api/wallets/receiver404/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(300.00));
    }

    @Test
    void transfer_shouldReturnUnprocessableEntity_whenSameWallet() throws Exception {
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user505\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/transfer")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fromUserId\":\"user505\",\"toUserId\":\"user505\",\"amount\":100.00,\"description\":\"Same wallet transfer\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Cannot transfer to the same wallet"))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").isNotEmpty());
    }

    @Test
    void transfer_shouldReturnUnprocessableEntity_whenInsufficientFunds() throws Exception {
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"sender606\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"receiver606\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/transfer")
                        .contentType(APPLICATION_JSON)
                        .content("{\"fromUserId\":\"sender606\",\"toUserId\":\"receiver606\",\"amount\":500.00,\"description\":\"Insufficient funds transfer\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Insufficient funds"))
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").isNotEmpty());
    }

    @Test
    void createWallet_shouldReturnConflict_whenWalletAlreadyExists() throws Exception {
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"duplicate707\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"duplicate707\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Wallet already exists for user: duplicate707"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").isNotEmpty());
    }

    // ===== TESTES DE IDEMPOTÊNCIA =====

    @Test
    void deposit_shouldWorkNormally_whenFirstTimeWithIdempotencyKey() throws Exception {
        // Criar carteira
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"idempotent_user_001\"}"))
                .andExpect(status().isCreated());

        String idempotencyKey = "deposit-123e4567-e89b-12d3-a456-426614174000";

        // Primeiro depósito com idempotency key
        ResultActions firstResult = mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content("{\"userId\":\"idempotent_user_001\",\"amount\":100.00,\"description\":\"First deposit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(100.00))
                .andExpect(jsonPath("$.id").isNotEmpty());

        // Verificar saldo
        mockMvc.perform(get("/api/wallets/idempotent_user_001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void deposit_shouldReturnSameTransaction_whenDuplicateIdempotencyKey() throws Exception {
        // Criar carteira
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"idempotent_user_002\"}"))
                .andExpect(status().isCreated());

        String idempotencyKey = "deposit-987fcdeb-51a2-43d7-8b9e-123456789abc";

        // Primeiro depósito
        ResultActions firstResult = mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content("{\"userId\":\"idempotent_user_002\",\"amount\":150.00,\"description\":\"Original deposit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(150.00));

        String firstTransactionId = firstResult.andReturn().getResponse().getContentAsString();
        String extractedId = firstTransactionId.substring(firstTransactionId.indexOf("\"id\":\"") + 6,
                                                         firstTransactionId.indexOf("\",", firstTransactionId.indexOf("\"id\":\"")));

        // Segundo depósito com mesmo idempotency key (mas valores diferentes para testar que não são processados)
        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content("{\"userId\":\"idempotent_user_002\",\"amount\":999.99,\"description\":\"Duplicate attempt\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(extractedId))
                .andExpect(jsonPath("$.amount").value(150.00)) // Deve retornar o valor original
                .andExpect(jsonPath("$.description").value("Original deposit")); // Deve retornar a descrição original

        // Verificar que o saldo não mudou (não foi processado duas vezes)
        mockMvc.perform(get("/api/wallets/idempotent_user_002/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.00));
    }

    @Test
    void withdraw_shouldReturnSameTransaction_whenDuplicateIdempotencyKey() throws Exception {
        // Criar carteira e adicionar fundos
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"idempotent_user_003\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"idempotent_user_003\",\"amount\":500.00,\"description\":\"Initial funds\"}"))
                .andExpect(status().isCreated());

        String idempotencyKey = "withdraw-456789ab-cdef-1234-5678-90abcdef1234";

        // Primeiro saque
        ResultActions firstResult = mockMvc.perform(post("/api/wallets/withdraw")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content("{\"userId\":\"idempotent_user_003\",\"amount\":200.00,\"description\":\"Original withdrawal\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balanceAfter").value(300.00));

        String firstTransactionId = firstResult.andReturn().getResponse().getContentAsString();
        String extractedId = firstTransactionId.substring(firstTransactionId.indexOf("\"id\":\"") + 6,
                                                         firstTransactionId.indexOf("\",", firstTransactionId.indexOf("\"id\":\"")));

        // Segundo saque com mesmo idempotency key
        mockMvc.perform(post("/api/wallets/withdraw")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content("{\"userId\":\"idempotent_user_003\",\"amount\":999.99,\"description\":\"Duplicate withdrawal attempt\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(extractedId))
                .andExpect(jsonPath("$.amount").value(200.00)) // Valor original
                .andExpect(jsonPath("$.description").value("Original withdrawal")); // Descrição original

        // Verificar que o saldo não mudou (não foi processado duas vezes)
        mockMvc.perform(get("/api/wallets/idempotent_user_003/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(300.00));
    }

    @Test
    void transfer_shouldReturnSameTransaction_whenDuplicateIdempotencyKey() throws Exception {
        // Criar carteiras
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"idempotent_sender_004\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"idempotent_receiver_004\"}"))
                .andExpect(status().isCreated());

        // Adicionar fundos ao remetente
        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"idempotent_sender_004\",\"amount\":1000.00,\"description\":\"Initial funds\"}"))
                .andExpect(status().isCreated());

        String idempotencyKey = "transfer-aabbccdd-1122-3344-5566-778899aabbcc";

        // Primeira transferência
        ResultActions firstResult = mockMvc.perform(post("/api/wallets/transfer")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content("{\"fromUserId\":\"idempotent_sender_004\",\"toUserId\":\"idempotent_receiver_004\",\"amount\":250.00,\"description\":\"Original transfer\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.balanceAfter").value(750.00));

        String firstTransactionId = firstResult.andReturn().getResponse().getContentAsString();
        String extractedId = firstTransactionId.substring(firstTransactionId.indexOf("\"id\":\"") + 6,
                                                         firstTransactionId.indexOf("\",", firstTransactionId.indexOf("\"id\":\"")));

        // Segunda transferência com mesmo idempotency key
        mockMvc.perform(post("/api/wallets/transfer")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content("{\"fromUserId\":\"idempotent_sender_004\",\"toUserId\":\"idempotent_receiver_004\",\"amount\":999.99,\"description\":\"Duplicate transfer attempt\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(extractedId))
                .andExpect(jsonPath("$.amount").value(250.00)) // Valor original
                .andExpect(jsonPath("$.description").value("Original transfer")); // Descrição original

        // Verificar que os saldos não mudaram (não foi processado duas vezes)
        mockMvc.perform(get("/api/wallets/idempotent_sender_004/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(750.00));

        mockMvc.perform(get("/api/wallets/idempotent_receiver_004/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(250.00));
    }

    @Test
    void idempotencyKey_shouldBeScopedPerUser_whenSameKeyUsedByDifferentUsers() throws Exception {
        // Criar duas carteiras
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user_A_005\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"user_B_005\"}"))
                .andExpect(status().isCreated());

        String sameIdempotencyKey = "shared-key-12345678-abcd-efgh-ijkl-123456789abc";

        // Usuário A faz depósito
        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", sameIdempotencyKey)
                        .content("{\"userId\":\"user_A_005\",\"amount\":100.00,\"description\":\"User A deposit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.balanceAfter").value(100.00));

        // Usuário B faz depósito com mesma idempotency key (deve funcionar pois é usuário diferente)
        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", sameIdempotencyKey)
                        .content("{\"userId\":\"user_B_005\",\"amount\":200.00,\"description\":\"User B deposit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.balanceAfter").value(200.00));

        // Verificar saldos diferentes (ambas transações foram processadas)
        mockMvc.perform(get("/api/wallets/user_A_005/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));

        mockMvc.perform(get("/api/wallets/user_B_005/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.00));
    }

    @Test
    void idempotencyKey_shouldBeScopedPerOperation_whenSameKeyUsedForDifferentOperations() throws Exception {
        // Criar carteira
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"multi_op_user_006\"}"))
                .andExpect(status().isCreated());

        String sameIdempotencyKey = "multi-op-key-87654321-zyxw-vutsrq-ponm-987654321abc";

        // Depósito com idempotency key
        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", sameIdempotencyKey)
                        .content("{\"userId\":\"multi_op_user_006\",\"amount\":300.00,\"description\":\"Deposit with shared key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(300.00))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(300.00));

        // Saque com mesma idempotency key (deve funcionar pois é operação diferente)
        mockMvc.perform(post("/api/wallets/withdraw")
                        .contentType(APPLICATION_JSON)
                        .header("Idempotency-Key", sameIdempotencyKey)
                        .content("{\"userId\":\"multi_op_user_006\",\"amount\":100.00,\"description\":\"Withdrawal with shared key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balanceAfter").value(200.00));

        // Verificar saldo final (ambas operações foram processadas)
        mockMvc.perform(get("/api/wallets/multi_op_user_006/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.00));
    }

    @Test
    void deposit_shouldWorkWithoutIdempotencyKey() throws Exception {
        // Criar carteira
        mockMvc.perform(post("/api/wallets")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"no_idem_user_007\"}"))
                .andExpect(status().isCreated());

        // Depósito sem idempotency key
        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"no_idem_user_007\",\"amount\":50.00,\"description\":\"No idempotency key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(50.00));

        // Segundo depósito sem idempotency key (deve ser processado normalmente)
        mockMvc.perform(post("/api/wallets/deposit")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"no_idem_user_007\",\"amount\":25.00,\"description\":\"Another deposit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(75.00));

        // Verificar saldo final (ambos depósitos foram processados)
        mockMvc.perform(get("/api/wallets/no_idem_user_007/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(75.00));
    }
}
