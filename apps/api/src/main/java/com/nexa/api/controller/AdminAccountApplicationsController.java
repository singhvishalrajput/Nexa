package com.nexa.api.controller;

import static com.nexa.api.onboarding.AccountApplicationDtos.parseUuid;

import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.onboarding.AccountApplicationDtos.ActionRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.CashReceiptRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.ReasonRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.RefundRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.ReviewDocumentRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.ReviewRequest;
import com.nexa.api.onboarding.AccountApplicationService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin URL security is supplemented by fresh active-role and non-self checks in the service. */
@RestController
@RequestMapping("/api/v1/admin/account-applications")
public class AdminAccountApplicationsController {
  private static final Set<String> STATUSES = Set.of(
      "DRAFT", "PENDING_REVIEW", "CHANGES_REQUESTED", "APPROVED_AWAITING_CASH", "CASH_RECEIVED",
      "OPENED", "REJECTED", "CANCELLED", "REFUND_PENDING", "REFUNDED");
  private final AccountApplicationService service;

  public AdminAccountApplicationsController(AccountApplicationService service) {
    this.service = service;
  }

  @GetMapping("/readiness")
  public ResponseEntity<Map<String, Object>> readiness() {
    return OnboardingHttpResponses.privateResponse(service.readiness());
  }

  @GetMapping
  public ResponseEntity<Map<String, Object>> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 100
        || (status != null && !STATUSES.contains(status))) {
      throw new InvalidRequestException("Choose a valid application status, page and size.");
    }
    return OnboardingHttpResponses.privateResponse(service.listAdmin(status, page, size));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
    return OnboardingHttpResponses.privateResponse(service.getAdmin(parseUuid(id)));
  }

  @GetMapping("/{id}/identity")
  public ResponseEntity<Map<String, Object>> revealIdentity(@PathVariable String id) {
    return OnboardingHttpResponses.privateResponse(service.revealIdentity(parseUuid(id)));
  }

  @PostMapping("/{id}/documents/{documentId}/review")
  public ResponseEntity<Map<String, Object>> reviewDocument(
      @PathVariable String id,
      @PathVariable String documentId,
      @Valid @RequestBody ReviewDocumentRequest request) {
    return OnboardingHttpResponses.privateResponse(
        service.reviewDocument(parseUuid(id), parseUuid(documentId), request));
  }

  @PostMapping("/{id}/review")
  public ResponseEntity<Map<String, Object>> reviewApplication(
      @PathVariable String id, @Valid @RequestBody ReviewRequest request) {
    return OnboardingHttpResponses.privateResponse(service.reviewApplication(parseUuid(id), request));
  }

  @PostMapping("/{id}/cash-receipts")
  public ResponseEntity<Map<String, Object>> receiveCash(
      @PathVariable String id, @Valid @RequestBody CashReceiptRequest request) {
    return OnboardingHttpResponses.privateResponse(service.receiveCash(parseUuid(id), request));
  }

  @PostMapping("/{id}/open")
  public ResponseEntity<Map<String, Object>> open(
      @PathVariable String id, @Valid @RequestBody ActionRequest request) {
    return OnboardingHttpResponses.privateResponse(service.open(parseUuid(id), request));
  }

  @PostMapping("/{id}/refund-request")
  public ResponseEntity<Map<String, Object>> requestRefund(
      @PathVariable String id, @Valid @RequestBody ReasonRequest request) {
    return OnboardingHttpResponses.privateResponse(service.requestRefund(parseUuid(id), request));
  }

  @PostMapping("/{id}/refund")
  public ResponseEntity<Map<String, Object>> refund(
      @PathVariable String id, @Valid @RequestBody RefundRequest request) {
    return OnboardingHttpResponses.privateResponse(service.refund(parseUuid(id), request));
  }

  @GetMapping("/{id}/documents/{documentId}/download")
  public ResponseEntity<byte[]> download(
      @PathVariable String id, @PathVariable String documentId) {
    return OnboardingHttpResponses.document(
        service.downloadAdmin(parseUuid(id), parseUuid(documentId)));
  }
}
