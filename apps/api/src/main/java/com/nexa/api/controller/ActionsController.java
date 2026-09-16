package com.nexa.api.controller;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.service.ActionPreparationService;
import com.nexa.api.service.TransferQueryService;


import com.nexa.api.service.TransferQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ActionsController {
  public record Request(
      @NotBlank @Pattern(regexp = "START_TRANSFER|PAY_BILL|PAY_CARD|CANCEL_MANDATE")
          String operation,
      @NotBlank @Size(max = 80) String accountId,
      @NotBlank @Size(max = 80) String targetId,
      @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount) {}

  private final ActionPreparationService actions;
  private final TransferQueryService transfers;

  public ActionsController(ActionPreparationService actions, TransferQueryService transfers) {
    this.actions = actions;
    this.transfers = transfers;
  }

  @PostMapping("/actions/prepare")
  public BankingModels.PreparedAction prepare(@Valid @RequestBody Request request) {
    return actions.prepare(
        request.operation(), request.accountId(), request.targetId(), request.amount());
  }

  @GetMapping("/transfers/{id}")
  public BankingModels.Transfer transfer(@PathVariable String id) {
    return transfers.detail(id);
  }
}
