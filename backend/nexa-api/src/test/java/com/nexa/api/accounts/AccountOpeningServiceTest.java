package com.nexa.api.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexa.api.identity.CurrentUserProvider;
import com.nexa.api.identity.UserQueryService;
import com.nexa.api.ledger.OpeningLedgerService;
import com.nexa.api.shared.errors.ConflictException;
import com.nexa.api.transactions.TransactionRecordingService;

@ExtendWith(MockitoExtension.class)
class AccountOpeningServiceTest {

    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private UserQueryService userQueryService;
    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private OpeningLedgerService openingLedgerService;
    @Mock private TransactionRecordingService transactionRecordingService;

    private AccountOpeningService service;

    @BeforeEach
    void setUp() {
        service = new AccountOpeningService(
                currentUserProvider,
                userQueryService,
                bankAccountRepository,
                openingLedgerService,
                transactionRecordingService,
                new BigDecimal("100000.00"));
        when(currentUserProvider.userId()).thenReturn("usr_customer");
        when(userQueryService.requireUser("usr_customer")).thenReturn(new UserQueryService.UserSummary(
                "usr_customer", "customer@example.com", "Customer", null, "ACTIVE", "CUSTOMER"));
    }

    @Test
    void createsAccountLedgerAndVisibleOpeningTransaction() {
        when(bankAccountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(bankAccountRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(openingLedgerService.recordOpeningCredit(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyString(), any())).thenReturn("jen_opening");

        AccountResponse response = service.open(new OpenAccountRequest("Primary account", "SAVINGS", "INR"));

        assertThat(response.availableBalance()).isEqualByComparingTo("100000.00");
        assertThat(response.accountNumberMasked()).startsWith("•••• ");
        ArgumentCaptor<BankAccountEntity> accountCaptor = ArgumentCaptor.forClass(BankAccountEntity.class);
        verify(bankAccountRepository).saveAndFlush(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getVersion()).isNull();
        verify(openingLedgerService).recordOpeningCredit(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyString(), any());
        verify(transactionRecordingService).recordOpeningCredit(
                anyString(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyString(), any());
    }

    @Test
    void rejectsASecondAccountBeforeWritingLedgerData() {
        when(bankAccountRepository.existsByUserId("usr_customer")).thenReturn(true);

        assertThatThrownBy(() -> service.open(new OpenAccountRequest("Another", "CURRENT", "INR")))
                .isInstanceOf(ConflictException.class);

        verify(bankAccountRepository, never()).saveAndFlush(any());
        verify(openingLedgerService, never()).recordOpeningCredit(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyString(), any());
    }
}
