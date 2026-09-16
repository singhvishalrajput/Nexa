package com.nexa.api.controller;
import com.nexa.api.beans.Account;
import com.nexa.api.service.MoneyTransferService;


import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/money-transfers")
public class MoneyTransferController {
  public record Request(
      @NotBlank @Pattern(regexp = "[0-9]{1,19}") String sourceAccountId,
      @Pattern(regexp = "[0-9]{1,19}") String destinationAccountId,
      @Pattern(
              regexp = "[0-9]{6,30}",
              message = "Enter the full Nexa account number using 6–30 digits.")
          String destinationAccountNumber,
      @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount) {}

  private final MoneyTransferService service;

  public MoneyTransferController(MoneyTransferService service) {
    this.service = service;
  }

  @PostMapping("/prepare")
  public MoneyTransferService.Receipt prepare(@Valid @RequestBody Request request) {
    return service.prepare(request);
  }

  @GetMapping("/{id}")
  public MoneyTransferService.Receipt status(@PathVariable String id) {
    return service.status(id);
  }

  @PostMapping("/{id}/confirm")
  public MoneyTransferService.Receipt confirm(@PathVariable String id) {
    return service.confirm(id);
  }
}
