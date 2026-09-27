package com.nexa.api.controller;

import com.nexa.api.beans.BankingModels;
import com.nexa.api.exep.ApiError;
import com.nexa.api.service.CardApplicationService;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cards")
public class CardApplicationsController {
  private final CardApplicationService service;
  public CardApplicationsController(CardApplicationService service) { this.service = service; }
  @PostMapping("/applications")
  public BankingModels.Card apply(@RequestBody CardApplicationService.ApplicationRequest request) { return service.apply(request); }
  @PostMapping("/{id}/block")
  public BankingModels.Card block(@PathVariable String id) { return service.setBlocked(id, true); }
  @PostMapping("/{id}/unblock")
  public BankingModels.Card unblock(@PathVariable String id) { return service.setBlocked(id, false); }
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> denied(AccessDeniedException error) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
        "https://api.nexa/errors/access-denied", "Forbidden", 403, "ACCESS_DENIED", error.getMessage(),
        MDC.get("correlationId"), Instant.now(), List.of()));
  }
}
