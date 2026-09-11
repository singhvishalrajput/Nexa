package com.nexa.api.banking;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/credit-cards")
public class CreditCardsController {
  private final CardQueryService service;

  public CreditCardsController(CardQueryService service) {
    this.service = service;
  }

  @GetMapping
  public List<BankingModels.Card> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    return service.creditCards(status, page, size);
  }

  @GetMapping("/{id}")
  public BankingModels.Card detail(@PathVariable String id) {
    return service.creditCard(id);
  }

  @GetMapping("/{id}/transactions")
  public List<com.nexa.api.conversations.BankingContent.Transaction> transactions(
      @PathVariable String id) {
    var rows = detail(id).transactions();
    return rows == null ? List.of() : rows;
  }
}
