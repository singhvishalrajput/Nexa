package com.nexa.api.controller;

import com.nexa.api.external.ExternalTransferService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/external-transfers")
public class ExternalTransfersController {
  private final ExternalTransferService service;
  public ExternalTransfersController(ExternalTransferService service) { this.service = service; }
  @GetMapping("/readiness") public ExternalTransferService.Readiness readiness() { return service.readiness(); }
  @PostMapping("/prepare") public ExternalTransferService.Receipt prepare(@Valid @RequestBody ExternalTransferService.PrepareRequest request) { return service.prepare(request); }
  @GetMapping public List<ExternalTransferService.Receipt> list(@RequestParam(required = false) String payeeId) { return service.list(payeeId); }
  @GetMapping("/{id}") public ExternalTransferService.Receipt status(@PathVariable String id) { return service.status(id); }
  @PostMapping("/{id}/confirm") public ExternalTransferService.Receipt confirm(@PathVariable String id) { return service.confirm(id); }
  @PostMapping("/{id}/refresh") public ExternalTransferService.Receipt refresh(@PathVariable String id) { return service.refresh(id); }
  @PostMapping("/{id}/cancel") public ExternalTransferService.Receipt cancel(@PathVariable String id) { return service.cancel(id); }
}
