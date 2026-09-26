package com.nexa.api.controller;

import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.onboarding.AccountApplicationDtos.DocumentDownload;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

final class OnboardingHttpResponses {
  private static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;

  private OnboardingHttpResponses() {}

  static <T> ResponseEntity<T> privateResponse(T body) {
    return privateResponse(HttpStatus.OK, body);
  }

  static <T> ResponseEntity<T> privateResponse(HttpStatus status, T body) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header(HttpHeaders.EXPIRES, "0")
        .header("X-Content-Type-Options", "nosniff")
        .body(body);
  }

  static ResponseEntity<byte[]> document(DocumentDownload document) {
    String extension = switch (document.mediaType()) {
      case "application/pdf" -> "pdf";
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      default -> throw new IllegalStateException("Unsupported stored document type.");
    };
    byte[] bytes = document.bytes();
    if (bytes.length == 0 || bytes.length > MAX_DOCUMENT_BYTES) {
      throw new IllegalStateException("Invalid stored document size.");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(document.mediaType()))
        .contentLength(bytes.length)
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header(HttpHeaders.EXPIRES, "0")
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("account-proof." + extension).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox; default-src 'none'")
        .body(bytes);
  }

  static void validateUploadParts(HttpServletRequest request, MultipartFile file) {
    if (file.isEmpty()) throw new InvalidRequestException("Choose a non-empty document.");
    if (file.getSize() > MAX_DOCUMENT_BYTES) {
      throw new MaxUploadSizeExceededException(MAX_DOCUMENT_BYTES);
    }
    Map<String, Integer> names = new HashMap<>();
    try {
      for (Part part : request.getParts()) {
        names.merge(part.getName(), 1, Integer::sum);
        if ("request".equals(part.getName()) && part.getSize() > 8192) {
          throw new InvalidRequestException("Document metadata is too large.");
        }
      }
    } catch (IOException | ServletException malformed) {
      throw new InvalidRequestException("Provide valid document upload parts.");
    }
    if (!names.keySet().equals(Set.of("request", "file"))
        || names.values().stream().anyMatch(count -> count != 1)
        || request.getParameterMap().keySet().stream().anyMatch(name -> !"request".equals(name))) {
      throw new InvalidRequestException("Provide exactly one request part and one file part.");
    }
  }
}
