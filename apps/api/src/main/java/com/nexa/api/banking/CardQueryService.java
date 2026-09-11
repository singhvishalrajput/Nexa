package com.nexa.api.banking;

import com.nexa.api.identity.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
public class CardQueryService extends ProductQueryService<BankingModels.Card> {
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
      throw new com.nexa.api.shared.errors.ResourceNotFoundException(
          "The credit card was not found.");
    return card;
  }
}
