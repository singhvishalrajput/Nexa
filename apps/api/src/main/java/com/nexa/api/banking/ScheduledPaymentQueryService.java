package com.nexa.api.banking;

import com.nexa.api.identity.CurrentUserProvider;
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
