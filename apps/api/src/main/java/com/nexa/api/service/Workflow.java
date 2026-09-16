package com.nexa.api.service;


import java.time.OffsetDateTime;
import java.util.List;

/**
 * Versioned snapshots. IDs are opaque; money is decimal text. No credentials or raw transcripts.
 */
public record Workflow(
    int version,
    String id,
    String operation,
    String status,
    String field,
    String accountId,
    String targetId,
    String accountLabel,
    String targetLabel,
    String amount,
    String currency,
    String message,
    List<Choice> choices,
    OffsetDateTime expiresAt,
    boolean confirmationRequired,
    boolean executionAvailable,
    String reference) {
  public record Choice(String id, String label) {}

  public record Command(String actionId, String type, String value) {}
}
