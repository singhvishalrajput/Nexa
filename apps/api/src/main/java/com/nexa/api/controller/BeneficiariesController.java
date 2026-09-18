package com.nexa.api.controller;

import com.nexa.api.beans.BankingModels.Beneficiary;
import com.nexa.api.service.BeneficiaryQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiariesController {
  private final BeneficiaryQueryService service;

  public BeneficiariesController(BeneficiaryQueryService service) {
    this.service = service;
  }

  @GetMapping
  public List<Beneficiary> list() {
    return service.list();
  }

  @PostMapping
  public Beneficiary create(@RequestBody BeneficiaryQueryService.SaveRequest request) {
    return service.save(null, request);
  }

  @PutMapping("/{id}")
  public Beneficiary link(
      @PathVariable String id, @RequestBody BeneficiaryQueryService.SaveRequest request) {
    return service.save(id, request);
  }

  @GetMapping("/{id}")
  public Beneficiary detail(@PathVariable String id) {
    return service.detail(id);
  }
}
