package com.nexa.api.service;

import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountCategory;
import com.nexa.api.beans.AccountResponse;
import com.nexa.api.beans.AccountType;
import com.nexa.api.beans.OpenAccountRequest;
import com.nexa.api.exep.ConflictException;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.repository.AccountDao;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compatibility eligibility boundary. Direct opening is not allowed: the manual
 * application/review/recorded-cash workflow is the only permitted opening path.
 */
@Service
@Transactional(readOnly = true)
public class AccountOpeningService {
  private static final int MINIMUM_AGE = 18;
  private static final String MINIMUM_OPENING_DEPOSIT = "1000.00";
  private static final String UNAVAILABLE =
      "Direct account opening is unavailable. Use the manual document-review application"
          + " workflow with admin verification and recorded cash funding."
          + " No account has been created and no money has been credited by this request.";

  private final CurrentUserProvider current;
  private final UserQueryService users;
  private final AccountDao accounts;
  private final Clock clock;
  private final BusinessDateResolver dates;

  public AccountOpeningService(
      CurrentUserProvider current,
      UserQueryService users,
      AccountDao accounts,
      Clock clock,
      BusinessDateResolver dates) {
    this.current = current;
    this.users = users;
    this.accounts = accounts;
    this.clock = clock;
    this.dates = dates;
  }

  public record OpeningRequirements(
      int minimumAge,
      String minimumOpeningDeposit,
      LocalDate businessDate,
      LocalDate latestDateOfBirth,
      List<String> allowedAccountTypes,
      boolean verificationAvailable,
      boolean fundingAvailable,
      boolean openingAvailable,
      String reason) {}

  public OpeningRequirements openingRequirements() {
    var user = requireCustomer();
    LocalDate today = businessDate();
    boolean hasSavings = hasSavings(user.id());
    return new OpeningRequirements(
        MINIMUM_AGE,
        MINIMUM_OPENING_DEPOSIT,
        today,
        today.minusYears(MINIMUM_AGE),
        hasSavings ? List.of() : List.of("SAVINGS"),
        false,
        false,
        false,
        hasSavings
            ? "This customer already has a SAVINGS account. Only one personal savings account"
                + " is allowed. " + UNAVAILABLE
            : UNAVAILABLE);
  }

  public AccountResponse open(OpenAccountRequest request) {
    var user = requireCustomer();
    validate(request);
    if ("CURRENT".equals(request.accountType()))
      throw new ConflictException(
          "Current accounts require a verified organisation."
              + " Organisation onboarding is not available yet.");
    if (hasSavings(user.id()))
      throw new ConflictException(
          "This customer already has a SAVINGS account. Only one personal savings account"
              + " is allowed.");

    // Do not add a caller-supplied verified flag or configuration shortcut here. The next
    // approved step must verify evidence and funds on the server before allowing a write.
    throw new ConflictException(UNAVAILABLE);
  }

  private UserQueryService.UserSummary requireCustomer() {
    var user = users.requireUser(current.userId());
    if (!"CUSTOMER".equals(user.role()))
      throw new ConflictException("Only customers can open a Nexa bank account.");
    if (!"ACTIVE".equals(user.status()))
      throw new ConflictException("The customer profile is not active.");
    return user;
  }

  private boolean hasSavings(String userId) {
    return accounts.findByCustomerUserIdOrderByCreatedAtAsc(userId).stream()
        .anyMatch(this::isPersonalSavings);
  }

  private boolean isPersonalSavings(Account account) {
    // Existing CLOSED accounts also count until replacement/reopening policy is approved.
    return account.getAccountCategory() == AccountCategory.CUSTOMER
        && account.getAccountType() == AccountType.SAVINGS;
  }

  private LocalDate businessDate() {
    return LocalDate.now(clock.withZone(dates.zone()));
  }

  private void validate(OpenAccountRequest request) {
    if (request == null)
      throw new InvalidRequestException("Account details are required.");
    if (!"SAVINGS".equals(request.accountType()) && !"CURRENT".equals(request.accountType()))
      throw new InvalidRequestException("Choose a supported account type.");
    if (!"INR".equals(request.currencyCode()))
      throw new InvalidRequestException("Only INR accounts are supported.");
    LocalDate birth = request.dateOfBirth();
    if (birth == null || birth.getYear() < 1 || birth.getYear() > 9999)
      throw new InvalidRequestException("Provide a valid date of birth.");
    LocalDate today = businessDate();
    if (!birth.isBefore(today))
      throw new InvalidRequestException("Date of birth must be before today.");
    if (birth.isAfter(today.minusYears(MINIMUM_AGE)))
      throw new InvalidRequestException("You must be at least 18 years old to open an account.");
  }
}
