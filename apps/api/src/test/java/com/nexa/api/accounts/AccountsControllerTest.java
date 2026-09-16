package com.nexa.api.accounts;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountBalanceResponse;
import com.nexa.api.beans.AccountResponse;
import com.nexa.api.beans.AccountType;
import com.nexa.api.beans.PageResponse;
import com.nexa.api.beans.TransactionResponse;
import com.nexa.api.config.CorrelationIdFilter;
import com.nexa.api.controller.AccountsController;
import com.nexa.api.exep.ApiExceptionHandler;
import com.nexa.api.service.AccountOpeningService;
import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.TransactionQueryService;


import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexa.api.config.CorrelationIdFilter;
import com.nexa.api.exep.ApiExceptionHandler;
import com.nexa.api.beans.PageResponse;
import com.nexa.api.service.TransactionQueryService;
import com.nexa.api.beans.TransactionResponse;

@WebMvcTest(AccountsController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({CorrelationIdFilter.class, ApiExceptionHandler.class})
class AccountsControllerTest {

    private static final String ACCOUNT_ID = "acc_01JDEMO000000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountQueryService accountQueryService;

    @MockitoBean
    private TransactionQueryService transactionQueryService;

    @MockitoBean
    private AccountOpeningService accountOpeningService;

    @Test
    void listsCurrentCustomersAccounts() throws Exception {
        AccountResponse account = account();
        when(accountQueryService.currentAccounts()).thenReturn(List.of(account));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ACCOUNT_ID))
                .andExpect(jsonPath("$[0].accountNumberMasked").value("•••• 4291"))
                .andExpect(jsonPath("$[0].availableBalance").value(96280.0));
    }

    @Test
    void opensAnAccountAndReturnsCreated() throws Exception {
        when(accountOpeningService.open(org.mockito.ArgumentMatchers.any())).thenReturn(account());

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Primary account",
                                  "accountType": "SAVINGS",
                                  "currencyCode": "INR",
                                  "dateOfBirth": "1990-01-01",
                                  "address": "Mumbai"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.availableBalance").value(96280.0));
    }

    @Test
    void returnsAuthoritativeBalance() throws Exception {
        AccountBalanceResponse balance = new AccountBalanceResponse(
                ACCOUNT_ID, "INR", new BigDecimal("96280.0000"), new BigDecimal("96280.0000"), OffsetDateTime.parse("2026-09-04T10:00:00Z"));
        when(accountQueryService.balance(ACCOUNT_ID)).thenReturn(balance);

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.currencyCode").value("INR"))
                .andExpect(jsonPath("$.availableBalance").value(96280.0));
    }

    @Test
    void returnsPaginatedTransactions() throws Exception {
        TransactionResponse transaction = new TransactionResponse(
                "txn_01JDEMO000000000000001", ACCOUNT_ID, "DEMO-SALARY-20260901", "SALARY",
                "Nexa Demo Employer", "Income", new BigDecimal("82400.0000"), "INR", "POSTED",
                OffsetDateTime.parse("2026-09-01T10:00:00Z"));
        when(transactionQueryService.transactions(ACCOUNT_ID, null, null, null, 0, 50))
                .thenReturn(new PageResponse<>(List.of(transaction), 0, 50, 1, 1));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].merchantName").value("Nexa Demo Employer"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private AccountResponse account() {
        return new AccountResponse(
                ACCOUNT_ID,
                "Primary account",
                "•••• 4291",
                "SAVINGS",
                "INR",
                new BigDecimal("96280.0000"),
                new BigDecimal("96280.0000"),
                "ACTIVE",
                OffsetDateTime.parse("2026-09-04T10:00:00Z"));
    }
}
