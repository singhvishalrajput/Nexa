package com.nexa.api.controller;

import com.nexa.api.service.AdminLoanService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/loans")
public class AdminLoansController {
  private final AdminLoanService service;

  public AdminLoansController(AdminLoanService service) {
    this.service = service;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    return service.requests();
  }

  @PostMapping("/{id}/approve")
  public Map<String, Object> approve(
      @PathVariable String id, @RequestBody AdminLoanService.Decision decision) {
    return service.decide(id, decision, true);
  }

  @PostMapping("/{id}/reject")
  public Map<String, Object> reject(
      @PathVariable String id, @RequestBody AdminLoanService.Decision decision) {
    return service.decide(id, decision, false);
  }
}
