package com.nexa.api.service;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.repository.BankingProductRepository;


import com.nexa.api.service.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class BillQueryService extends ProductQueryService<BankingModels.Bill> {
  public java.util.List<BankingModels.Bill> all(String status) {
    var result = new java.util.ArrayList<BankingModels.Bill>();
    for (int page = 0; ; page++) {
      var rows = list(status, page, 100);
      result.addAll(rows);
      if (rows.size() < 100) return result;
    }
  }

  public BillQueryService(BankingProductRepository repository, CurrentUserProvider user) {
    super(repository, user, BankingProductRepository.Kind.BILL, BankingModels.Bill.class);
  }
}
