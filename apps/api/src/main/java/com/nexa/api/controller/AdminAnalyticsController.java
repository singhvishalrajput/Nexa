package com.nexa.api.controller;

import com.nexa.api.service.AdminAnalyticsService;
import com.nexa.api.exep.ApiError;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController {
  private final AdminAnalyticsService service;

  public AdminAnalyticsController(AdminAnalyticsService service) { this.service = service; }

  @GetMapping
  public AdminAnalyticsService.Analytics read(@RequestParam(defaultValue = "30") int days) {
    return service.read(days);
  }

  // The global legacy handler otherwise turns a revoked administrator's denial into HTTP 500.
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> denied(AccessDeniedException ignored) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
        "https://api.nexa/errors/access-denied", "Forbidden", 403, "ACCESS_DENIED",
        "Administrator access is required to view analytics.", MDC.get("correlationId"),
        Instant.now(), List.of()));
  }
}
