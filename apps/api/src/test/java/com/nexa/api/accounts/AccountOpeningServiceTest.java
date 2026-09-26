package com.nexa.api.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountCategory;
import com.nexa.api.beans.AccountStatus;
import com.nexa.api.beans.AccountType;
import com.nexa.api.beans.Customer;
import com.nexa.api.beans.OpenAccountRequest;
import com.nexa.api.exep.ConflictException;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.repository.AccountDao;
import com.nexa.api.service.AccountOpeningService;
import com.nexa.api.service.BusinessDateResolver;
import com.nexa.api.service.UserQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountOpeningServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-21T20:00:00Z"), ZoneOffset.UTC);
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 22);
  private static final LocalDate CUTOFF = LocalDate.of(2008, 9, 22);
  private final UserQueryService users = mock(UserQueryService.class);
  private final AccountDao accounts = mock(AccountDao.class);

  private AccountOpeningService service(Clock clock) {
    return new AccountOpeningService(
        () -> "owner", users, accounts, clock, new BusinessDateResolver(clock, BUSINESS_ZONE));
  }

  private AccountOpeningService service() {
    return service(CLOCK);
  }

  private void customerUser() {
    when(users.requireUser("owner"))
        .thenReturn(new UserQueryService.UserSummary(
            "owner", "owner@example.test", "Customer", null, "ACTIVE", "CUSTOMER"));
    when(accounts.findByCustomerUserIdOrderByCreatedAtAsc("owner")).thenReturn(List.of());
  }

  @Test
  void requirementsExposeServerBusinessDateAndNoClientCanEnableUnavailableServices() {
    customerUser();
    var requirements = service().openingRequirements();

    assertThat(requirements.minimumAge()).isEqualTo(18);
    assertThat(new BigDecimal(requirements.minimumOpeningDeposit())).isEqualByComparingTo("1000");
    assertThat(requirements.businessDate()).isEqualTo(BUSINESS_DATE);
    assertThat(requirements.latestDateOfBirth()).isEqualTo(CUTOFF);
    assertThat(requirements.allowedAccountTypes()).containsExactly("SAVINGS");
    assertThat(requirements.verificationAvailable()).isFalse();
    assertThat(requirements.fundingAvailable()).isFalse();
    assertThat(requirements.openingAvailable()).isFalse();
    assertThat(requirements.reason()).isNotBlank();
    verify(accounts).findByCustomerUserIdOrderByCreatedAtAsc("owner");
    verifyNoMoreInteractions(accounts);
  }

  @ParameterizedTest
  @EnumSource(AccountStatus.class)
  void existingSavingsConsumesIndividualSlotEvenWhenBlockedOrClosed(AccountStatus status) {
    customerUser();
    when(accounts.findByCustomerUserIdOrderByCreatedAtAsc("owner"))
        .thenReturn(List.of(account(AccountType.SAVINGS, status)));

    assertThat(service().openingRequirements().allowedAccountTypes()).isEmpty();
    assertThatThrownBy(() -> service().open(request("SAVINGS", "INR", CUTOFF)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("SAVINGS");
    verify(accounts, org.mockito.Mockito.times(2))
        .findByCustomerUserIdOrderByCreatedAtAsc("owner");
    verifyNoMoreInteractions(accounts);
  }

  @Test
  void legacyCurrentAccountDoesNotConsumeTheIndividualsSavingsSlot() {
    customerUser();
    when(accounts.findByCustomerUserIdOrderByCreatedAtAsc("owner"))
        .thenReturn(List.of(account(AccountType.CURRENT, AccountStatus.ACTIVE)));
    assertThat(service().openingRequirements().allowedAccountTypes()).containsExactly("SAVINGS");
    assertThatThrownBy(() -> service().open(request("SAVINGS", "INR", CUTOFF)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("verification");
    verify(accounts, org.mockito.Mockito.times(2))
        .findByCustomerUserIdOrderByCreatedAtAsc("owner");
    verifyNoMoreInteractions(accounts);
  }

  @Test
  void individualCurrentAccountNeedsAnOrganisationAndCannotReachTheOpeningGate() {
    customerUser();
    assertThatThrownBy(() -> service().open(request("CURRENT", "INR", CUTOFF)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("organisation");
    // No account number allocation, save, balance change or deposit is possible.
    org.mockito.Mockito.verify(accounts, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.verify(accounts, org.mockito.Mockito.never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  static Stream<Arguments> invalidRequests() {
    return Stream.of(
        Arguments.of("missing request", null),
        Arguments.of("missing type", request(null, "INR", CUTOFF)),
        Arguments.of("unsupported type", request("LOAN", "INR", CUTOFF)),
        Arguments.of("case-sensitive type", request("savings", "INR", CUTOFF)),
        Arguments.of("missing currency", request("SAVINGS", null, CUTOFF)),
        Arguments.of("unsupported currency", request("SAVINGS", "USD", CUTOFF)),
        Arguments.of("missing birth date", request("SAVINGS", "INR", null)),
        Arguments.of("zero birth year", request("SAVINGS", "INR", LocalDate.of(0, 1, 1))),
        Arguments.of("birth year outside supported range", request("SAVINGS", "INR", LocalDate.of(10000, 1, 1))),
        Arguments.of("born today", request("SAVINGS", "INR", BUSINESS_DATE)),
        Arguments.of("future birth date", request("SAVINGS", "INR", BUSINESS_DATE.plusDays(1))),
        Arguments.of("one day under eighteen", request("SAVINGS", "INR", CUTOFF.plusDays(1))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRequests")
  void validatesRulesForInternalCallersBeforeAnyAccountMutation(String description, OpenAccountRequest request) {
    customerUser();
    assertThatThrownBy(() -> service().open(request))
        .as(description)
        .isInstanceOf(InvalidRequestException.class);
    verifyNoInteractions(accounts);
  }

  static Stream<LocalDate> adultBirthDates() {
    return Stream.of(CUTOFF, CUTOFF.minusDays(1), LocalDate.of(1990, 1, 1));
  }

  @ParameterizedTest
  @MethodSource("adultBirthDates")
  void adulthoodNeverSubstitutesForGenuineVerificationAndConfirmedFunding(LocalDate dateOfBirth) {
    customerUser();
    assertThatThrownBy(() -> service().open(request("SAVINGS", "INR", dateOfBirth)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("verification")
        .hasMessageContaining("funding");
    verify(accounts).findByCustomerUserIdOrderByCreatedAtAsc("owner");
    verifyNoMoreInteractions(accounts);
  }

  @Test
  void usesBusinessTimezoneRatherThanUtcForTheEighteenthBirthday() {
    customerUser();
    // The instant is September 21 in UTC but September 22 in India.
    assertThat(service().openingRequirements().latestDateOfBirth()).isEqualTo(CUTOFF);
    assertThatThrownBy(() -> service().open(request("SAVINGS", "INR", CUTOFF)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("verification");
  }

  @Test
  void leapDayBirthFollowsTheServerCutoffInsteadOfRoundingAnAgeInDays() {
    customerUser();
    Clock february = Clock.fixed(Instant.parse("2026-02-28T12:00:00Z"), ZoneOffset.UTC);
    Clock march = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);
    LocalDate leapBirthday = LocalDate.of(2008, 2, 29);
    assertThat(service(february).openingRequirements().latestDateOfBirth())
        .isEqualTo(LocalDate.of(2008, 2, 28));
    assertThatThrownBy(() -> service(february).open(request("SAVINGS", "INR", leapBirthday)))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service(march).open(request("SAVINGS", "INR", leapBirthday)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("verification");
  }

  @Test
  void administratorCannotRequestCustomerOnboardingOrItsRequirements() {
    when(users.requireUser("owner"))
        .thenReturn(new UserQueryService.UserSummary(
            "owner", "admin@example.test", "Admin", null, "ACTIVE", "ADMIN"));
    assertThatThrownBy(() -> service().openingRequirements()).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service().open(request("SAVINGS", "INR", CUTOFF)))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(accounts);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUSPENDED", "LOCKED"})
  void inactiveCustomerCannotReadRequirementsOrAttemptOpening(String customerStatus) {
    when(users.requireUser("owner"))
        .thenReturn(new UserQueryService.UserSummary(
            "owner", "owner@example.test", "Customer", null, customerStatus, "CUSTOMER"));
    assertThatThrownBy(() -> service().openingRequirements())
        .isInstanceOf(ConflictException.class).hasMessageContaining("not active");
    assertThatThrownBy(() -> service().open(request("SAVINGS", "INR", CUTOFF)))
        .isInstanceOf(ConflictException.class).hasMessageContaining("not active");
    verifyNoInteractions(accounts);
  }

  private static OpenAccountRequest request(String type, String currency, LocalDate birth) {
    return new OpenAccountRequest("Savings", type, currency, birth, "Mumbai" );
  }

  private Account account(AccountType type, AccountStatus status) {
    Customer customer = new Customer();
    customer.setUserId("owner");
    Account account = new Account();
    account.setId(1L);
    account.setCustomer(customer);
    account.setAccountCategory(AccountCategory.CUSTOMER);
    account.setAccountType(type);
    account.setStatus(status);
    return account;
  }
}
