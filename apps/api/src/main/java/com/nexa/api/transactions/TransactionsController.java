package com.nexa.api.transactions;

import com.nexa.api.shared.web.PageResponse;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionsController {
  private final TransactionQueryService service;

  public TransactionsController(TransactionQueryService service) {
    this.service = service;
  }

  @GetMapping
  public PageResponse<TransactionResponse> list(
      @RequestParam String accountId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) String direction,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    return service.transactions(accountId, category, from, to, page, size, direction, search);
  }

  @GetMapping("/{id}")
  public TransactionResponse detail(@PathVariable String id) {
    return service.detail(id);
  }
}
