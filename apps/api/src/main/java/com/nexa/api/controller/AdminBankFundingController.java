package com.nexa.api.controller;

import com.nexa.api.exep.ApiError;
import com.nexa.api.service.BankFundingService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/bank-funding")
public class AdminBankFundingController {
  private final BankFundingService service;
  public AdminBankFundingController(BankFundingService service) { this.service = service; }

  @GetMapping public BankFundingService.Overview overview() { return service.overview(); }

  @PostMapping("/receipts") public BankFundingService.Receipt record(@RequestBody BankFundingService.Request request) {
    return service.record(request);
  }

  @GetMapping("/receipts/by-request/{requestId}")
  public BankFundingService.Receipt byRequest(@PathVariable UUID requestId) { return service.byRequest(requestId); }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> denied(AccessDeniedException ignored) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("https://api.nexa/errors/access-denied", "Forbidden", 403,
        "ACCESS_DENIED", "Active administrator access is required for bank funding.", MDC.get("correlationId"), Instant.now(), List.of()));
  }
}
