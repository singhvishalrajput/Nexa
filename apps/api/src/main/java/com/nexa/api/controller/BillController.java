package com.nexa.api.controller;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.service.BillQueryService;


import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bills")
public class BillController {
  private final BillQueryService service;
  private final com.nexa.api.service.PaymentItemService operations;

  public BillController(BillQueryService service, com.nexa.api.service.PaymentItemService operations) {
    this.service = service;
    this.operations = operations;
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

  @PostMapping
  public java.util.Map<String, Object> create(@RequestBody com.nexa.api.service.PaymentItemService.BillRequest request) {
    return operations.createBill(request);
  }

  @PostMapping("/{id}/status")
  public java.util.Map<String, Object> status(@PathVariable String id, @RequestBody com.nexa.api.service.PaymentItemService.StatusRequest request) {
    return operations.setBillStatus(id, request);
  }

  @GetMapping("/{id}/payments")
  public List<BankingModels.Payment> payments(@PathVariable String id) {
    var rows = service.detail(id).paymentHistory();
    return rows == null ? List.of() : rows;
  }
}
