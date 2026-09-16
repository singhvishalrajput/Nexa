package com.nexa.api.service;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.repository.BankingProductRepository;


import com.nexa.api.service.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class ScheduledPaymentQueryService extends ProductQueryService<BankingModels.Payment> {
  public ScheduledPaymentQueryService(
      BankingProductRepository repository, CurrentUserProvider user) {
    super(
        repository,
        user,
        BankingProductRepository.Kind.SCHEDULED_PAYMENT,
        BankingModels.Payment.class);
  }
}
