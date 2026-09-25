package com.nexa.api.controller;

import com.nexa.api.beans.BankingModels;
import com.nexa.api.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cards")
public class CardActionsController {
  private final CardService cards;
  private final CardSupport support;
  public CardActionsController(CardService cards, CardSupport support) { this.cards = cards; this.support = support; }
  public record DebitRequest(@NotBlank String accountId) {}
  @GetMapping("/support")
  public Map<String, String> support() { return Map.of("phone", support.phone(), "email", support.email(), "message", support.guidance()); }
  @GetMapping("/bill-funding-accounts")
  public java.util.List<com.nexa.api.beans.AccountResponse> funding() { return cards.billFundingAccounts(); }
  @PostMapping("/digital-debit")
  public BankingModels.Card create(@Valid @RequestBody DebitRequest request) { return cards.createDebit(request.accountId()); }
}
