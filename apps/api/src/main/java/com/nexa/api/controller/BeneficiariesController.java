package com.nexa.api.controller;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.service.BeneficiaryQueryService;


import com.nexa.api.beans.BankingModels.Beneficiary;
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

  @GetMapping("/{id}")
  public Beneficiary detail(@PathVariable String id) {
    return service.detail(id);
  }
}
