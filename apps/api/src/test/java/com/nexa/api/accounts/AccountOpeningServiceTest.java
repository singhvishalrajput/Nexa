package com.nexa.api.accounts;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nexa.api.core.model.*;
import com.nexa.api.core.repository.*;
import com.nexa.api.core.service.*;
import com.nexa.api.identity.*;
import com.nexa.api.shared.errors.*;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountOpeningServiceTest {
  final UserQueryService users = mock(UserQueryService.class);
  final AccountDao accounts = mock(AccountDao.class);
  final CustomerDao customers = mock(CustomerDao.class);
  final AccountService accountService = mock(AccountService.class);
  final CustomerService customerService = mock(CustomerService.class);
  final AccountOpeningService service =
      new AccountOpeningService(
          () -> "owner", users, accounts, customers, accountService, customerService);
  final OpenAccountRequest request =
      new OpenAccountRequest("Primary", "SAVINGS", "INR", LocalDate.of(1990, 1, 1), "Mumbai");

  @Test
  void createsZeroBalanceAccountThroughAuthoritativeService() {
    when(users.requireUser("owner"))
        .thenReturn(
            new UserQueryService.UserSummary(
                "owner", "a@example.com", "Customer", null, "ACTIVE", "CUSTOMER"));
    var c = new Customer();
    c.setId(1L);
    c.setUserId("owner");
    c.setDateOfBirth(request.dateOfBirth());
    c.setAddress(request.address());
    when(customers.findByUserId("owner")).thenReturn(Optional.of(c));
    when(accountService.create(any()))
        .thenAnswer(
            call -> {
              Account a = call.getArgument(0);
              a.setId(2L);
              return a;
            });
    var response = service.open(request);
    assertThat(response.availableBalance()).isZero();
    assertThat(response.ledgerBalance()).isZero();
    assertThat(response.id()).isEqualTo("2");
    verify(accountService)
        .create(
            argThat(
                a -> a.getCustomer() == c && a.getAccountCategory() == AccountCategory.CUSTOMER));
    verifyNoInteractions(customerService);
  }

  @Test
  void rejectsNonCustomer() {
    when(users.requireUser("owner"))
        .thenReturn(
            new UserQueryService.UserSummary(
                "owner", "a@example.com", "Admin", null, "ACTIVE", "ADMIN"));
    assertThatThrownBy(() -> service.open(request)).isInstanceOf(ConflictException.class);
    verifyNoInteractions(accountService, customerService);
  }

  @Test
  void doesNotClaimExistingCustomerByEmail() {
    when(users.requireUser("owner"))
        .thenReturn(
            new UserQueryService.UserSummary(
                "owner", "a@example.com", "Customer", null, "ACTIVE", "CUSTOMER"));
    when(customers.findByEmail("a@example.com")).thenReturn(Optional.of(new Customer()));
    assertThatThrownBy(() -> service.open(request)).isInstanceOf(ConflictException.class);
    verifyNoInteractions(accountService, customerService);
  }
}
