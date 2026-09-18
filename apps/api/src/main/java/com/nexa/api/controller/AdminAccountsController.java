package com.nexa.api.controller;

import com.nexa.api.service.AdminAccountService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountsController {
  private final AdminAccountService service;

  public AdminAccountsController(AdminAccountService service) {
    this.service = service;
  }

  @GetMapping
  public List<AdminAccountService.View> list() {
    return service.list();
  }

  @PutMapping("/{id}")
  public AdminAccountService.View edit(
      @PathVariable long id, @RequestBody AdminAccountService.Edit request) {
    return service.edit(id, request);
  }

  @GetMapping("/{id}")
  public AdminAccountService.Workspace workspace(@PathVariable long id) {
    return service.workspace(id);
  }

  @GetMapping("/{id}/transactions")
  public AdminAccountService.Activity activity(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.activity(id, page, size);
  }

  @PostMapping("/{id}/adjustments")
  public Map<String, String> adjust(
      @PathVariable long id, @RequestBody AdminAccountService.Adjustment request) {
    return service.adjust(id, request);
  }

  @GetMapping("/{id}/audit")
  public List<Map<String, Object>> history(@PathVariable long id) {
    return service.history(id);
  }
}
