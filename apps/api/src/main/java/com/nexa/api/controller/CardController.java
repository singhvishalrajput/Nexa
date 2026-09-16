package com.nexa.api.controller;
import com.nexa.api.beans.BankingContent;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.service.CardQueryService;


import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cards")
public class CardController {
  private final CardQueryService service;

  public CardController(CardQueryService service) {
    this.service = service;
  }

  @GetMapping
  public List<BankingModels.Card> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    return service.list(status, page, size);
  }

  @GetMapping("/{id}")
  public BankingModels.Card detail(@PathVariable String id) {
    return service.detail(id);
  }

  @GetMapping("/{id}/transactions")
  public List<com.nexa.api.beans.BankingContent.Transaction> transactions(
      @PathVariable String id) {
    var rows = service.detail(id).transactions();
    return rows == null ? List.of() : rows;
  }
}
