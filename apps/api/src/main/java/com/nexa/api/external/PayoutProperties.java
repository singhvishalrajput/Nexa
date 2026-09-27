package com.nexa.api.external;

import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Immutable for the lifetime of the process; credentials and environment cannot change per request. */
@Component
public final class PayoutProperties {
  private final ExternalPayoutProvider.Environment mode;
  private final String clientId;
  private final String clientSecret;
  private final Duration timeout;

  public PayoutProperties(
      @Value("${nexa.payouts.mode:DISABLED}") String mode,
      @Value("${nexa.payouts.client-id:}") String clientId,
      @Value("${nexa.payouts.client-secret:}") String clientSecret,
      @Value("${nexa.payouts.timeout-seconds:10}") int timeoutSeconds) {
    try {
      this.mode = ExternalPayoutProvider.Environment.valueOf(mode.strip().toUpperCase(Locale.ROOT));
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("nexa.payouts.mode must be DISABLED, SANDBOX or LIVE");
    }
    this.clientId = clientId == null ? "" : clientId.strip();
    this.clientSecret = clientSecret == null ? "" : clientSecret.strip();
    if (timeoutSeconds < 1 || timeoutSeconds > 30) {
      throw new IllegalArgumentException("nexa.payouts.timeout-seconds must be between 1 and 30");
    }
    this.timeout = Duration.ofSeconds(timeoutSeconds);
  }

  public ExternalPayoutProvider.Environment mode() { return mode; }

  String clientId() { return clientId; }

  String clientSecret() { return clientSecret; }

  Duration timeout() { return timeout; }

  public boolean available() { return unavailableReason() == null; }

  public String unavailableReason() {
    if (mode == ExternalPayoutProvider.Environment.DISABLED) {
      return "External bank transfers are not enabled.";
    }
    if (mode == ExternalPayoutProvider.Environment.LIVE) {
      return "Live external bank transfers are unavailable in this sandbox release.";
    }
    if (!validHeader(clientId) || !validHeader(clientSecret)) {
      return "Cashfree sandbox credentials have not been configured correctly.";
    }
    return null;
  }

  private static boolean validHeader(String value) {
    return !value.isEmpty() && value.length() <= 1024
        && value.chars().allMatch(character -> character >= 33 && character <= 126);
  }

  @Override
  public String toString() {
    return "PayoutProperties[mode=" + mode + ", credentials=REDACTED]";
  }
}
