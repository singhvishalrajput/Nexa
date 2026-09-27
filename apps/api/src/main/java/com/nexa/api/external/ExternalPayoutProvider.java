package com.nexa.api.external;

import java.math.BigDecimal;

/** Provider observations only. The caller owns durable idempotency, correlation and ledger changes. */
public interface ExternalPayoutProvider {
  enum Environment { DISABLED, SANDBOX, LIVE }

  enum State { PENDING, SUCCESS, FAILED, REVERSED, UNKNOWN, NOT_FOUND }

  Environment environment();

  boolean available();

  String unavailableReason();

  /** Submit once using a persisted transfer ID. An uncertain result must be recovered through status. */
  Outcome submit(PayoutRequest request);

  /** NOT_FOUND is an observation, never permission to refund or create another payout. */
  Outcome status(String transferId);

  record PayoutRequest(
      String transferId,
      String bankAccountNumber,
      String ifsc,
      String beneficiaryName,
      BigDecimal amount) {
    @Override
    public String toString() {
      return "PayoutRequest[transferId=" + transferId + ", recipient=REDACTED]";
    }
  }

  /** Correlation fields are null when the provider did not supply them. Never expose this as a DTO. */
  record Outcome(
      State state,
      String providerTransferId,
      String providerStatus,
      String statusCode,
      String utr,
      String message,
      String transferId,
      BigDecimal amount,
      String bankAccountNumber,
      String ifsc) {
    @Override
    public String toString() {
      return "Outcome[state=" + state + ", transferId=" + transferId + ", recipient=REDACTED]";
    }
  }
}
