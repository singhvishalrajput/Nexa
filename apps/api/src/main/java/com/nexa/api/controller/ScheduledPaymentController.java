package com.nexa.api.controller;
import com.nexa.api.service.ScheduledPaymentService;
import com.nexa.api.exep.ApiError;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;


import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scheduled-payments")
public class ScheduledPaymentController {
  private final ScheduledPaymentService service;

  public ScheduledPaymentController(ScheduledPaymentService service) {
    this.service = service;
  }

  @GetMapping
  public List<ScheduledPaymentService.Receipt> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    return service.list(status, page, size);
  }

  @GetMapping("/{id}")
  public ScheduledPaymentService.Receipt detail(@PathVariable String id) {
    return service.detail(id);
  }

  @GetMapping("/requirements")
  public ScheduledPaymentService.Requirements requirements() { return service.requirements(); }

  @PostMapping("/prepare")
  public ScheduledPaymentService.Receipt prepare(@RequestBody ScheduledPaymentService.Request request) { return service.prepare(request); }

  @PostMapping("/{id}/confirm")
  public ScheduledPaymentService.Receipt confirm(@PathVariable String id,@RequestBody ScheduledPaymentService.Authorization request) { return service.confirm(id,request); }

  @PostMapping("/{id}/cancel")
  public ScheduledPaymentService.Receipt cancel(@PathVariable String id) { return service.cancel(id); }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> denied(AccessDeniedException ignored) {
    return ResponseEntity.status(403).body(new ApiError("https://api.nexa/errors/access-denied","Forbidden",403,"ACCESS_DENIED",
        "An active customer session is required.",MDC.get("correlationId"),Instant.now(),List.of()));
  }
}
