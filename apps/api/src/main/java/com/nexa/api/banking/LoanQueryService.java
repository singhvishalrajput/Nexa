package com.nexa.api.banking;

import com.nexa.api.identity.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class LoanQueryService extends ProductQueryService<BankingModels.Loan> {
  public LoanQueryService(BankingProductRepository repository, CurrentUserProvider user) {
    super(repository, user, BankingProductRepository.Kind.LOAN, BankingModels.Loan.class);
  }
}
