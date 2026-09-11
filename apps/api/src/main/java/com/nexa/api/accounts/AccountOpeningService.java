package com.nexa.api.accounts;

import com.nexa.api.core.model.*;
import com.nexa.api.core.repository.*;
import com.nexa.api.core.service.*;
import com.nexa.api.identity.*;
import com.nexa.api.shared.errors.*;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountOpeningService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final CurrentUserProvider current;
  private final UserQueryService users;
  private final AccountDao accounts;
  private final CustomerDao customers;
  private final AccountService accountService;
  private final CustomerService customerService;

  public AccountOpeningService(
      CurrentUserProvider current,
      UserQueryService users,
      AccountDao accounts,
      CustomerDao customers,
      AccountService accountService,
      CustomerService customerService) {
    this.current = current;
    this.users = users;
    this.accounts = accounts;
    this.customers = customers;
    this.accountService = accountService;
    this.customerService = customerService;
  }

  @Transactional
  public AccountResponse open(OpenAccountRequest request) {
    var user = users.requireUser(current.userId());
    if (!"CUSTOMER".equals(user.role()))
      throw new ConflictException("Only customers can open a Nexa bank account.");
    Customer customer =
        customers
            .findByUserId(user.id())
            .orElseGet(
                () -> {
                  if (customers.findByEmail(user.email()).isPresent())
                    throw new ConflictException(
                        "This customer already exists. Ask an administrator to link the banking"
                            + " profile to your login.");
                  Customer c = new Customer();
                  c.setFullName(user.fullName());
                  c.setEmail(user.email());
                  c.setPhoneNumber(user.phoneNumber());
                  c.setDateOfBirth(request.dateOfBirth());
                  c.setAddress(request.address());
                  c.setUserId(user.id());
                  return customerService.create(c);
                });
    if (customer.getDateOfBirth() == null || customer.getAddress() == null) {
      customer.setDateOfBirth(request.dateOfBirth());
      customer.setAddress(request.address());
      customers.save(customer);
    }
    Account a = new Account();
    a.setAccountNumber(uniqueNumber());
    a.setAccountName(request.displayName().trim());
    a.setAccountType(AccountType.valueOf(request.accountType()));
    a.setAccountCategory(AccountCategory.CUSTOMER);
    a.setCustomer(customer);
    a.setCurrencyCode(request.currencyCode());
    return AccountQueryService.toResponse(accountService.create(a));
  }

  private String uniqueNumber() {
    for (int attempt = 0; attempt < 10; attempt++) {
      StringBuilder n = new StringBuilder("9");
      while (n.length() < 12) n.append(RANDOM.nextInt(10));
      if (!accounts.existsByAccountNumber(n.toString())) return n.toString();
    }
    throw new ConflictException("Unable to allocate an account number.");
  }
}
