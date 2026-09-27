package com.nexa.api.controller;

import com.nexa.api.service.BillPaymentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bill-payments")
public class BillPaymentsController {
  private final BillPaymentService service;

  public BillPaymentsController(BillPaymentService service) { this.service = service; }

  @PostMapping("/prepare")
  public BillPaymentService.Receipt prepare(@Valid @RequestBody BillPaymentService.PrepareRequest request) {
    return service.prepare(request);
  }

  @GetMapping
  public List<BillPaymentService.Receipt> history(@RequestParam String billId) { return service.history(billId); }

  @GetMapping("/{id}")
  public BillPaymentService.Receipt status(@PathVariable String id) { return service.status(id); }

  @PostMapping("/{id}/confirm")
  public BillPaymentService.Receipt confirm(@PathVariable String id) { return service.confirm(id); }

  @PostMapping("/{id}/cancel")
  public BillPaymentService.Receipt cancel(@PathVariable String id) { return service.cancel(id); }
}
