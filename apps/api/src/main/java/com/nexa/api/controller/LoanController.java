package com.nexa.api.controller;

import com.nexa.api.beans.BankingModels;
import com.nexa.api.service.LoanQueryService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

  @Autowired
  private com.nexa.api.service.LoanCalculationService calculation;

  @Autowired
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
  public List<?> payments(@PathVariable String id) {
    var loan = service.detail(id);
    if (loan.terms() != null && loan.terms().tenureMonths() != null)
      return operations.loanPayments(id);
    var rows = loan.paymentHistory();
    return rows == null ? List.of() : rows;
  }

  @PostMapping
  public ResponseEntity<?> create(
      @RequestBody com.nexa.api.service.CreditMandateService.LoanRequest request) {
    var result = operations.createLoan(request);
    return org.springframework.http.ResponseEntity.status(request.scheduled() ? 201 : 200)
        .body(request.scheduled() ? service.detail(result.get("PRODUCT_ID").toString()) : result);
  }

  @PostMapping("/quote")
  public com.nexa.api.beans.LoanModels.Quote quote(
      @RequestBody com.nexa.api.beans.LoanModels.QuoteRequest request) {
    return calculation.quote(request);
  }

  @PostMapping("/{id}/accept")
  public BankingModels.Loan accept(@PathVariable String id) {
    operations.disburse(id);
    return service.detail(id);
  }

  @GetMapping("/{id}/schedule")
  public List<com.nexa.api.beans.LoanModels.Installment> schedule(@PathVariable String id) {
    return operations.schedule(id);
  }

  @GetMapping("/{id}/repayment-options")
  public com.nexa.api.beans.LoanModels.RepaymentOptions repaymentOptions(@PathVariable String id) {
    return operations.repaymentOptions(id);
  }

  @PostMapping("/{id}/installments/{installmentId}/pay")
  public com.nexa.api.beans.LoanModels.Payment pay(
      @PathVariable String id, @PathVariable String installmentId) {
    return operations.payInstallment(id, installmentId);
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
