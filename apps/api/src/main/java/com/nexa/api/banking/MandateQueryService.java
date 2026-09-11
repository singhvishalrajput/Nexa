package com.nexa.api.banking;

import com.nexa.api.identity.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class MandateQueryService extends ProductQueryService<BankingModels.Mandate> {
  public MandateQueryService(BankingProductRepository repository, CurrentUserProvider user) {
    super(repository, user, BankingProductRepository.Kind.MANDATE, BankingModels.Mandate.class);
  }
}
