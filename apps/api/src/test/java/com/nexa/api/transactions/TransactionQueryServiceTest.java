package com.nexa.api.transactions;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.BankTransaction;
import com.nexa.api.beans.Customer;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.TransactionDao;
import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.TransactionQueryService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexa.api.service.AccountQueryService;
import com.nexa.api.repository.TransactionDao;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TransactionQueryServiceTest {
  final AccountQueryService accounts = mock(AccountQueryService.class);
  final TransactionDao repo = mock(TransactionDao.class);
  final TransactionQueryService service =
      new TransactionQueryService(accounts, repo, () -> "owner");

  @Test
  void detailNeverReturnsAnotherOwnersTransaction() {
    var customer = new Customer();
    customer.setUserId("other");
    var account = new Account();
    account.setCustomer(customer);
    var tx = new BankTransaction();
    tx.setSourceAccount(account);
    when(repo.findById("other")).thenReturn(Optional.of(tx));
    assertThatThrownBy(() -> service.detail("other")).isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.detail("missing"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void rejectsInvalidFiltersBeforeReadingData() {
    assertThatThrownBy(() -> service.transactions("1", null, null, null, -1, 5, null, null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.transactions("1", null, null, null, 0, 5, "SIDEWAYS", null))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
            () ->
                service.transactions(
                    "1",
                    null,
                    LocalDate.of(2026, 9, 10),
                    LocalDate.of(2026, 9, 1),
                    0,
                    5,
                    null,
                    null))
        .isInstanceOf(InvalidRequestException.class);
    verifyNoInteractions(repo);
  }

  @Test
  void listRequiresOwnedAccount() {
    when(accounts.requireOwnedEntity("2")).thenThrow(new ResourceNotFoundException("Missing"));
    assertThatThrownBy(() -> service.transactions("2", null, null, null, 0, 10))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(repo);
  }
}
