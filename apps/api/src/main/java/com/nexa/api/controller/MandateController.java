package com.nexa.api.controller;

import com.nexa.api.beans.BankingModels;
import com.nexa.api.service.MandateQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mandates")
public class MandateController {
  @org.springframework.beans.factory.annotation.Autowired
  private com.nexa.api.service.CreditMandateService operations;

  private final MandateQueryService service;

  public MandateController(MandateQueryService service) {
    this.service = service;
  }

  @GetMapping
  public List<BankingModels.Mandate> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    return service.list(status, page, size);
  }

  @GetMapping("/{id}")
  public BankingModels.Mandate detail(@PathVariable String id) {
    return service.detail(id);
  }

  @PostMapping
  public java.util.Map<String, Object> create(
      @RequestBody com.nexa.api.service.CreditMandateService.MandateRequest request) {
    return operations.createMandate(request);
  }

  @GetMapping("/{id}/authorization")
  public java.util.Map<String, Object> authorization(@PathVariable String id) {
    return operations.mandate(id, false);
  }

  @PostMapping("/{id}/activate")
  public java.util.Map<String, Object> activate(@PathVariable String id) {
    return operations.activate(id);
  }

  @PostMapping({"/{id}/revoke", "/{id}/cancel"})
  public java.util.Map<String, Object> revoke(@PathVariable String id) {
    return operations.revoke(id);
  }

  @PostMapping("/{id}/execute")
  public java.util.Map<String, String> execute(
      @PathVariable String id,
      @RequestBody com.nexa.api.service.CreditMandateService.Execution request) {
    return java.util.Map.of("transactionId", operations.execute(id, request));
  }

  @GetMapping("/{id}/history")
  public java.util.List<java.util.Map<String, Object>> history(@PathVariable String id) {
    return operations.mandateHistory(id);
  }
}
