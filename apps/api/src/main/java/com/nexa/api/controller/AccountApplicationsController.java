package com.nexa.api.controller;

import static com.nexa.api.onboarding.AccountApplicationDtos.parseUuid;

import com.nexa.api.onboarding.AccountApplicationDtos.ActionRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.CreateRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.IdentityRequest;
import com.nexa.api.onboarding.AccountApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ownership and active-customer checks remain mandatory at every service entry point. */
@RestController
@RequestMapping("/api/v1/account-applications")
public class AccountApplicationsController {
  private final AccountApplicationService service;

  public AccountApplicationsController(AccountApplicationService service) {
    this.service = service;
  }

  @GetMapping("/requirements")
  public ResponseEntity<Map<String, Object>> requirements() {
    return OnboardingHttpResponses.privateResponse(service.requirements());
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateRequest request) {
    return OnboardingHttpResponses.privateResponse(HttpStatus.CREATED, service.create(request));
  }

  @GetMapping
  public ResponseEntity<List<Map<String, Object>>> listMine() {
    return OnboardingHttpResponses.privateResponse(service.listMine());
  }

  @GetMapping("/{id}")
  public ResponseEntity<Map<String, Object>> getMine(@PathVariable String id) {
    return OnboardingHttpResponses.privateResponse(service.getMine(parseUuid(id)));
  }

  @PostMapping("/{id}/submit")
  public ResponseEntity<Map<String, Object>> submit(
      @PathVariable String id, @Valid @RequestBody ActionRequest request) {
    return OnboardingHttpResponses.privateResponse(service.submit(parseUuid(id), request));
  }

  @PostMapping("/{id}/identity")
  public ResponseEntity<Map<String, Object>> updateIdentity(
      @PathVariable String id, @Valid @RequestBody IdentityRequest request) {
    return OnboardingHttpResponses.privateResponse(service.updateIdentity(parseUuid(id), request));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<Map<String, Object>> cancel(
      @PathVariable String id, @Valid @RequestBody ActionRequest request) {
    return OnboardingHttpResponses.privateResponse(service.cancel(parseUuid(id), request));
  }

  @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> uploadDocument(@PathVariable String id) {
    return OnboardingHttpResponses.privateResponse(service.uploadDocument(parseUuid(id), null, null));
  }

  @GetMapping("/{id}/documents/{documentId}/download")
  public ResponseEntity<byte[]> downloadMine(
      @PathVariable String id, @PathVariable String documentId) {
    return OnboardingHttpResponses.document(
        service.downloadMine(parseUuid(id), parseUuid(documentId)));
  }
}
