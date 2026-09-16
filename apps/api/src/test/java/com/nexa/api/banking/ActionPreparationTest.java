package com.nexa.api.banking;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountResponse;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.ActionPreparationService;
import com.nexa.api.service.BeneficiaryQueryService;
import com.nexa.api.service.BillQueryService;
import com.nexa.api.service.CardQueryService;
import com.nexa.api.service.MandateQueryService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexa.api.service.BeneficiaryQueryService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ActionPreparationTest {
  final AccountQueryService accounts = mock(AccountQueryService.class);
  final BeneficiaryQueryService beneficiaries = mock(BeneficiaryQueryService.class);
  final BillQueryService bills = mock(BillQueryService.class);
  final MandateQueryService mandates = mock(MandateQueryService.class);
  final CardQueryService cards = mock(CardQueryService.class);
  final ActionPreparationService service =
      new ActionPreparationService(accounts, beneficiaries, bills, mandates, cards);

  void account() {
    when(accounts.requireOwnedAccount("acc_1"))
        .thenReturn(
            new AccountResponse(
                "acc_1",
                "Savings",
                "•••• 1234",
                "SAVINGS",
                "INR",
                new BigDecimal("1000"),
                new BigDecimal("1000"),
                "ACTIVE",
                OffsetDateTime.now()));
  }

  @Test
  void preparationRequiresOwnedVerifiedTargetsAndNeverClaimsExecution() {
    account();
    when(beneficiaries.detail("ben_1"))
        .thenReturn(
            new BankingModels.Beneficiary("ben_1", "Demo", "Demo Bank", "•••• 1234", "ACTIVE"));
    var result = service.prepare("START_TRANSFER", "acc_1", "ben_1", new BigDecimal("100"));
    assertThat(result.status()).isEqualTo("PREPARED");
    assertThat(result.confirmationRequired()).isTrue();
    assertThat(result.executionAvailable()).isFalse();
    verify(accounts).requireOwnedAccount("acc_1");
    verify(beneficiaries).detail("ben_1");
    assertThatThrownBy(
            () -> service.prepare("START_TRANSFER", "acc_1", "ben_1", new BigDecimal("2000")))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void rejectsCrossOwnerAndInactiveTargets() {
    account();
    when(beneficiaries.detail("ben_other")).thenThrow(new ResourceNotFoundException("Not found"));
    assertThatThrownBy(
            () -> service.prepare("START_TRANSFER", "acc_1", "ben_other", BigDecimal.ONE))
        .isInstanceOf(ResourceNotFoundException.class);
    when(beneficiaries.detail("ben_1"))
        .thenReturn(new BankingModels.Beneficiary("ben_1", "Demo", null, "1234", "SUSPENDED"));
    assertThatThrownBy(() -> service.prepare("START_TRANSFER", "acc_1", "ben_1", BigDecimal.ONE))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void paidBillsCannotBePreparedAgain() {
    account();
    when(bills.detail("bil_1"))
        .thenReturn(
            new BankingModels.Bill(
                "bil_1",
                "Demo",
                "100",
                null,
                "INR",
                null,
                "PAID",
                "acc_1",
                "REF",
                null,
                null,
                java.util.List.of()));
    assertThatThrownBy(() -> service.prepare("PAY_BILL", "acc_1", "bil_1", null))
        .isInstanceOf(InvalidRequestException.class);
  }
}
