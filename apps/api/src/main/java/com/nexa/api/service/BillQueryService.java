package com.nexa.api.service;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.repository.BankingProductRepository;


import com.nexa.api.service.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class BillQueryService extends ProductQueryService<BankingModels.Bill> {
  public BillQueryService(BankingProductRepository repository, CurrentUserProvider user) {
    super(repository, user, BankingProductRepository.Kind.BILL, BankingModels.Bill.class);
  }
}
