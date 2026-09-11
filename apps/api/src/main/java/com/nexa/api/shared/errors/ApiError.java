package com.nexa.api.shared.errors;

import java.time.Instant;
import java.util.List;

public record ApiError(
    String type,
    String title,
    int status,
    String code,
    String detail,
    String traceId,
    Instant timestamp,
    List<FieldError> fieldErrors) {

  @com.fasterxml.jackson.annotation.JsonProperty("response")
  public ErrorResponse response() {
    return new ErrorResponse("ERROR", detail, code);
  }

  public record ErrorResponse(String type, String message, String errorCode) {}

  public record FieldError(String field, String message) {}
}
