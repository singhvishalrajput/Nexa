package com.nexa.api.banking;

import com.nexa.api.identity.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class BillQueryService extends ProductQueryService<BankingModels.Bill> {
  public BillQueryService(BankingProductRepository repository, CurrentUserProvider user) {
    super(repository, user, BankingProductRepository.Kind.BILL, BankingModels.Bill.class);
  }
}
