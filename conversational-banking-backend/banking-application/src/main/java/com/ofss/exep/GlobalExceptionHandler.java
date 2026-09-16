package com.ofss.exep;

import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException e) {
		return error(e, HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler({ BadRequestException.class, IllegalArgumentException.class })
	ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
		return error(e, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(UnauthorizedException.class)
	ResponseEntity<Map<String, String>> unauthorized(UnauthorizedException e) {
		return error(e, HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Map<String, String>> forbidden(AccessDeniedException e) {
		return error(e, HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Map<String, String>> other(Exception e) {
		return error(e, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private ResponseEntity<Map<String, String>> error(Exception e, HttpStatus status) {
		return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
	}
}
