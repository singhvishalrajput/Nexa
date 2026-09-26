package com.nexa.api.controller;

import com.nexa.api.exep.ApiError;
import com.nexa.api.onboarding.IdentifierProtector.ProtectionUnavailableException;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Scope is deliberately limited: existing controllers retain their established error contract. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
    AccountApplicationsController.class, AdminAccountApplicationsController.class
})
public class OnboardingExceptionHandler {
  @ExceptionHandler(ProtectionUnavailableException.class)
  ResponseEntity<ApiError> identityUnavailable(ProtectionUnavailableException ignored) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_CONFIGURATION_UNAVAILABLE",
        "Private identity protection is unavailable. Please try again later.");
  }
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> denied(AccessDeniedException ignored) {
    return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
        "You are not allowed to perform this account-application action.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> conflict(DataIntegrityViolationException ignored) {
    return error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT",
        "The application conflicts with current records. Refresh it before trying again.");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> oversized(MaxUploadSizeExceededException ignored) {
    return error(HttpStatus.CONTENT_TOO_LARGE, "DOCUMENT_TOO_LARGE",
        "The document exceeds the allowed upload size.");
  }

  @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class,
      HandlerMethodValidationException.class})
  ResponseEntity<ApiError> malformed(Exception ignored) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
        "Check the account-application request fields.");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiError> unsupported(HttpMediaTypeNotSupportedException ignored) {
    return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
        "Use JSON for account-application requests. Document uploads are no longer accepted.");
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String code, String detail) {
    ApiError body = new ApiError(
        "https://api.nexa/errors/" + code.toLowerCase(java.util.Locale.ROOT),
        status.getReasonPhrase(), status.value(), code, detail, MDC.get("correlationId"),
        Instant.now(), List.of());
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header("Pragma", "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .body(body);
  }
}
