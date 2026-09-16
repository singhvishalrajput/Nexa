package com.nexa.api.service;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.repository.BankingProductRepository;


import com.nexa.api.service.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class MandateQueryService extends ProductQueryService<BankingModels.Mandate> {
  public MandateQueryService(BankingProductRepository repository, CurrentUserProvider user) {
    super(repository, user, BankingProductRepository.Kind.MANDATE, BankingModels.Mandate.class);
  }
}
