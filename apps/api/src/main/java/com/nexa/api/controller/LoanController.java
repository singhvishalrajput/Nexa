package com.nexa.api.controller;

import com.nexa.api.beans.BankingModels;
import com.nexa.api.service.LoanQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {
  @org.springframework.beans.factory.annotation.Autowired
  private com.nexa.api.service.CreditMandateService operations;

  private final LoanQueryService service;

  public LoanController(LoanQueryService service) {
    this.service = service;
  }

  @GetMapping
  public List<BankingModels.Loan> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    return service.list(status, page, size);
  }

  @GetMapping("/{id}")
  public BankingModels.Loan detail(@PathVariable String id) {
    return service.detail(id);
  }

  @GetMapping("/{id}/payments")
  public List<BankingModels.Payment> payments(@PathVariable String id) {
    var rows = service.detail(id).paymentHistory();
    return rows == null ? List.of() : rows;
  }

  @PostMapping
  public java.util.Map<String, Object> create(
      @RequestBody com.nexa.api.service.CreditMandateService.LoanRequest request) {
    return operations.createLoan(request);
  }

  @GetMapping("/{id}/account")
  public java.util.Map<String, Object> account(@PathVariable String id) {
    return operations.loan(id, false);
  }

  @PostMapping("/{id}/disburse")
  public java.util.Map<String, String> disburse(@PathVariable String id) {
    return java.util.Map.of("transactionId", operations.disburse(id));
  }

  @PostMapping("/{id}/repay")
  public java.util.Map<String, String> repay(
      @PathVariable String id,
      @RequestBody com.nexa.api.service.CreditMandateService.Execution request) {
    return java.util.Map.of("transactionId", operations.repay(id, request));
  }
}
