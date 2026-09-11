package com.nexa.api.banking;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bills")
public class BillController {
  private final BillQueryService service;

  public BillController(BillQueryService service) {
    this.service = service;
  }

  @GetMapping
  public List<BankingModels.Bill> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    return service.list(status, page, size);
  }

  @GetMapping("/{id}")
  public BankingModels.Bill detail(@PathVariable String id) {
    return service.detail(id);
  }

  @GetMapping("/{id}/payments")
  public List<BankingModels.Payment> payments(@PathVariable String id) {
    var rows = service.detail(id).paymentHistory();
    return rows == null ? List.of() : rows;
  }
}
