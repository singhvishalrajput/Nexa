package com.nexa.api.service;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.repository.BankingProductRepository;


import com.nexa.api.service.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class LoanQueryService extends ProductQueryService<BankingModels.Loan> {
  public LoanQueryService(BankingProductRepository repository, CurrentUserProvider user) {
    super(repository, user, BankingProductRepository.Kind.LOAN, BankingModels.Loan.class);
  }
}
