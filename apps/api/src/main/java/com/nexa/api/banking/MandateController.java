package com.nexa.api.banking;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mandates")
public class MandateController {
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
}
