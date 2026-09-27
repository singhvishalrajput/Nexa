package com.nexa.api.controller;

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
@RequestMapping("/api/v1/admin/card-applications")
public class AdminCardApplicationsController {
  private final CardApplicationService service;
  public AdminCardApplicationsController(CardApplicationService service) { this.service = service; }
  @GetMapping
  public List<CardApplicationService.Application> list(@RequestParam(required=false) String status) { return service.applications(status); }
  @GetMapping("/{id}")
  public CardApplicationService.Application detail(@PathVariable String id) { return service.application(id); }
  @PostMapping("/{id}/approve")
  public CardApplicationService.Application approve(@PathVariable String id, @RequestBody CardApplicationService.Decision request) {
    return service.decide(id, request, true);
  }
  @PostMapping("/{id}/reject")
  public CardApplicationService.Application reject(@PathVariable String id, @RequestBody CardApplicationService.Decision request) {
    return service.decide(id, request, false);
  }
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> denied(AccessDeniedException error) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
        "https://api.nexa/errors/access-denied", "Forbidden", 403, "ACCESS_DENIED", error.getMessage(),
        MDC.get("correlationId"), Instant.now(), List.of()));
  }
}
