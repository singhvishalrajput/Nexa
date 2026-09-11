package com.nexa.api.shared.errors;

import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
    List<ApiError.FieldError> fieldErrors =
        exception.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();
    return response(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", fieldErrors);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> handleNotFound(NoResourceFoundException exception) {
    return response(
        HttpStatus.NOT_FOUND,
        "RESOURCE_NOT_FOUND",
        "The requested resource was not found.",
        List.of());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), List.of());
  }

  @ExceptionHandler(InvalidRequestException.class)
  ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException exception) {
    return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), List.of());
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ApiError> handleConflict(ConflictException exception) {
    return response(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", exception.getMessage(), List.of());
  }

  @ExceptionHandler(UnauthorizedException.class)
  ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException exception) {
    return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), List.of());
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    jakarta.validation.ConstraintViolationException.class
  })
  ResponseEntity<ApiError> malformed(Exception exception) {
    return response(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "Check the request fields and their formats.",
        List.of());
  }

  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  ResponseEntity<ApiError> unavailable(Exception exception) {
    return response(
        HttpStatus.SERVICE_UNAVAILABLE,
        "BANKING_UNAVAILABLE",
        "Banking information is temporarily unavailable. Please try again.",
        List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception exception) {
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "The request could not be completed.",
        List.of());
  }

  private ApiError.FieldError toFieldError(FieldError fieldError) {
    return new ApiError.FieldError(fieldError.getField(), fieldError.getDefaultMessage());
  }

  private ResponseEntity<ApiError> response(
      HttpStatus status, String code, String detail, List<ApiError.FieldError> fieldErrors) {
    ApiError body =
        new ApiError(
            "https://api.nexa/errors/" + code.toLowerCase().replace('_', '-'),
            status.getReasonPhrase(),
            status.value(),
            code,
            detail,
            MDC.get("correlationId"),
            Instant.now(),
            fieldErrors);
    return ResponseEntity.status(status).body(body);
  }
}
