package com.nexa.api.core.web;

import com.nexa.api.shared.errors.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(basePackages = "com.nexa.api.core.web")
public class GlobalExceptionHandler {
  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException e) {
    return error(e, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler({InvalidRequestException.class, IllegalArgumentException.class})
  ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
    return error(e, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> other(Exception e) {
    return error(e, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<Map<String, String>> error(Exception e, HttpStatus status) {
    return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
  }
}
