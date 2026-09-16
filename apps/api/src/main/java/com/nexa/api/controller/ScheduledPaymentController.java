package com.nexa.api.controller;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.service.ScheduledPaymentQueryService;


import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scheduled-payments")
public class ScheduledPaymentController {
  private final ScheduledPaymentQueryService service;

  public ScheduledPaymentController(ScheduledPaymentQueryService service) {
    this.service = service;
  }

  @GetMapping
  public List<BankingModels.Payment> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    return service.list(status, page, size);
  }

  @GetMapping("/{id}")
  public BankingModels.Payment detail(@PathVariable String id) {
    return service.detail(id);
  }
}
