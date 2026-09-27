package com.nexa.api.controller;

import com.nexa.api.beans.BankingModels.Beneficiary;
import com.nexa.api.external.ExternalPayeeService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/external-payees")
public class ExternalPayeesController {
  private final ExternalPayeeService service;
  public ExternalPayeesController(ExternalPayeeService service) { this.service = service; }
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public Beneficiary create(@RequestBody ExternalPayeeService.SaveRequest request) { return service.create(request); }
  @GetMapping public List<Beneficiary> list() { return service.list(); }
  @GetMapping("/{id}") public Beneficiary detail(@PathVariable String id) { return service.detail(id); }
}
