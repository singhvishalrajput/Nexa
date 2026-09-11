package com.nexa.api.customer;

import com.nexa.api.identity.CurrentUserProvider;
import com.nexa.api.identity.UserQueryService;
import com.nexa.api.identity.UserQueryService.UserSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerQueryService {

  private final com.nexa.api.core.repository.CustomerDao customers;
  private final com.nexa.api.core.service.CustomerService customerService;
  private final CurrentUserProvider currentUserProvider;
  private final UserQueryService userQueryService;

  CustomerQueryService(
      CurrentUserProvider currentUserProvider,
      UserQueryService userQueryService,
      com.nexa.api.core.repository.CustomerDao customers,
      com.nexa.api.core.service.CustomerService customerService) {
    this.customers = customers;
    this.customerService = customerService;
    this.currentUserProvider = currentUserProvider;
    this.userQueryService = userQueryService;
  }

  @Transactional(readOnly = true)
  public CustomerProfileResponse currentProfile() {
    String userId = currentUserProvider.userId();
    return toResponse(userQueryService.requireUser(userId));
  }

  @Transactional
  public CustomerProfileResponse updateCurrentProfile(UpdateProfileRequest request) {
    String userId = currentUserProvider.userId();
    customers
        .findByUserId(userId)
        .ifPresent(
            customer -> {
              var input = new com.nexa.api.core.model.Customer();
              input.setFullName(request.fullName().trim());
              input.setPhoneNumber(request.phoneNumber());
              input.setAddress(
                  request.address() == null ? customer.getAddress() : request.address().trim());
              customerService.update(customer.getId(), input);
            });
    return toResponse(
        userQueryService.updateProfile(userId, request.fullName(), request.phoneNumber()));
  }

  private CustomerProfileResponse toResponse(UserSummary user) {
    var customer = customers.findByUserId(user.id());
    return new CustomerProfileResponse(
        customer.map(c -> c.getId().toString()).orElse(user.id()),
        user.id(),
        customer.map(c -> c.getFullName()).orElse(user.fullName()),
        customer.map(c -> c.getEmail()).orElse(user.email()),
        customer.isPresent() ? customer.get().getPhoneNumber() : user.phoneNumber(),
        user.status(),
        user.role(),
        customer.map(c -> c.getAddress()).orElse(null));
  }
}
