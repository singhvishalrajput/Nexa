package com.nexa.api.service;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.BankingProductRepository;


import com.nexa.api.service.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class CardQueryService extends ProductQueryService<BankingModels.Card> {
  public java.util.List<BankingModels.Card> all() {
    var result = new java.util.ArrayList<BankingModels.Card>();
    for (int page = 0; ; page++) {
      var rows = list(null, page, 100);
      result.addAll(rows);
      if (rows.size() < 100) return result;
    }
  }
  public CardQueryService(BankingProductRepository repository, CurrentUserProvider user) {
    super(repository, user, BankingProductRepository.Kind.CARD, BankingModels.Card.class);
  }

  public java.util.List<BankingModels.Card> creditCards(String status, int page, int size) {
    return list(status, page, size).stream()
        .filter(card -> "CREDIT".equals(card.cardType()))
        .toList();
  }

  public BankingModels.Card creditCard(String id) {
    var card = detail(id);
    if (!"CREDIT".equals(card.cardType()))
      throw new com.nexa.api.exep.ResourceNotFoundException(
          "The credit card was not found.");
    return card;
  }
}
