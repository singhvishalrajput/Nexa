package com.nexa.api.external;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Cashfree Payouts v2 (2024-01-01), sandbox only. No automatic retries or redirected credentials. */
@Component
public final class CashfreePayoutProvider implements ExternalPayoutProvider {
  static final URI SANDBOX_ENDPOINT = URI.create("https://sandbox.cashfree.com/payout/transfers");
  static final int MAX_RESPONSE_BYTES = 64 * 1024;
  private final ObjectMapper json;
  private final PayoutProperties properties;
  private final HttpClient client;
  private final URI endpoint;

  @Autowired
  public CashfreePayoutProvider(ObjectMapper json, PayoutProperties properties) {
    this(json, properties, SANDBOX_ENDPOINT, false);
  }

  // This seam accepts loopback only and is inaccessible to HTTP/configuration input.
  CashfreePayoutProvider(ObjectMapper json, PayoutProperties properties, URI localTestEndpoint) {
    this(json, properties, localTestEndpoint, true);
  }

  private CashfreePayoutProvider(
      ObjectMapper json, PayoutProperties properties, URI endpoint, boolean localTest) {
    if (localTest && !("http".equals(endpoint.getScheme())
        && "127.0.0.1".equals(endpoint.getHost()) && endpoint.getPort() > 0
        && endpoint.getUserInfo() == null && endpoint.getQuery() == null
        && endpoint.getFragment() == null)) {
      throw new IllegalArgumentException("A provider test endpoint must use IPv4 loopback HTTP");
    }
    this.json = json;
    this.properties = properties;
    this.endpoint = endpoint;
    this.client = HttpClient.newBuilder()
        .connectTimeout(properties.timeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  @Override
  public Environment environment() { return properties.mode(); }

  @Override
  public boolean available() { return properties.available(); }

  @Override
  public String unavailableReason() { return properties.unavailableReason(); }

  @Override
  public Outcome submit(PayoutRequest payout) {
    if (!available()) return unknown(unavailableReason());
    validate(payout);
    byte[] body = json.writeValueAsBytes(Map.of(
        "transfer_id", payout.transferId(),
        "transfer_amount", payout.amount(),
        "transfer_currency", "INR",
        "transfer_mode", "imps",
        "beneficiary_details", Map.of(
            "beneficiary_name", payout.beneficiaryName(),
            "beneficiary_instrument_details", Map.of(
                "bank_account_number", payout.bankAccountNumber(), "bank_ifsc", payout.ifsc()))));
    return send(request(endpoint).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), false, payout.transferId());
  }

  @Override
  public Outcome status(String transferId) {
    if (!available()) return unknown(unavailableReason());
    validateTransferId(transferId);
    return send(request(URI.create(endpoint + "?transfer_id=" + transferId)).GET().build(),
        true, transferId);
  }

  private HttpRequest.Builder request(URI uri) {
    return HttpRequest.newBuilder(uri)
        .timeout(properties.timeout())
        .header("Accept", "application/json")
        .header("x-api-version", "2024-01-01")
        .header("x-client-id", properties.clientId())
        .header("x-client-secret", properties.clientSecret());
  }

  private Outcome send(HttpRequest request, boolean lookup, String expectedId) {
    CompletableFuture<HttpResponse<byte[]>> pending = null;
    try {
      pending = client.sendAsync(request, ignored -> new LimitedBodySubscriber());
      HttpResponse<byte[]> response = pending.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
      if (lookup && response.statusCode() == 404) {
        // Only a structured provider not-found response is useful; a proxy's 404 is unknown.
        JsonNode error = json.readTree(response.body());
        if ("transfer_not_found".equals(text(error, "code"))) {
          return new Outcome(State.NOT_FOUND, null, null, "TRANSFER_NOT_FOUND", null,
              "The sandbox provider has not found this transfer. Keep checking its status.",
              null, null, null, null);
        }
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return unknown("The sandbox provider could not confirm the transfer. Check its status before retrying.");
      }
      JsonNode body = json.readTree(response.body());
      String transferId = field(body, "transfer_id", "[A-Za-z0-9_]{1,40}");
      if (!expectedId.equals(transferId)) {
        return unknown("The sandbox provider response could not be matched to this transfer.");
      }
      String status = field(body, "status", "[A-Z_]{1,40}");
      String code = field(body, "status_code", "[A-Z_]{1,80}");
      State state = state(status, code);
      JsonNode instrument = body.path("beneficiary_details").path("beneficiary_instrument_details");
      BigDecimal amount = amount(body.path("transfer_amount"));
      String providerId = field(body, "cf_transfer_id", "[A-Za-z0-9_-]{1,100}");
      String account = field(instrument, "bank_account_number", "[A-Za-z0-9]{4,30}");
      // Cashfree's response schema calls this 'ifsc'; requests use 'bank_ifsc'.
      String ifsc = field(instrument, "ifsc", "[A-Z]{4}0[A-Z0-9]{6}");
      String bankIfsc = field(instrument, "bank_ifsc", "[A-Z]{4}0[A-Z0-9]{6}");
      if ((present(body, "transfer_amount") && amount == null)
          || (present(body, "cf_transfer_id") && providerId == null)
          || (present(instrument, "bank_account_number") && account == null)
          || (present(instrument, "ifsc") && ifsc == null)
          || (present(instrument, "bank_ifsc") && bankIfsc == null)
          || (ifsc != null && bankIfsc != null && !ifsc.equals(bankIfsc))) {
        return unknown("The sandbox provider returned invalid transfer details. Check its status.");
      }
      return new Outcome(state, providerId,
          status, code, field(body, "transfer_utr", "[A-Za-z0-9_-]{1,100}"), message(state),
          transferId, amount, account, ifsc == null ? bankIfsc : ifsc);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return unknown("The sandbox provider response was interrupted. Check the transfer status.");
    } catch (ExecutionException | TimeoutException | RuntimeException failure) {
      // Never expose provider bodies, exception messages, request headers or account identifiers.
      return unknown("The sandbox provider response is uncertain. Check the transfer status.");
    } finally {
      if (pending != null && !pending.isDone()) pending.cancel(true);
    }
  }

  private static State state(String status, String code) {
    if (status == null) return State.UNKNOWN;
    // A duplicate rejection says this ID already exists; it does not fail the original payout.
    if ("DUPLICATE_TRANSFER".equals(code)) return State.UNKNOWN;
    return switch (status) {
      case "SUCCESS" -> "COMPLETED".equals(code) ? State.SUCCESS : State.PENDING;
      case "FAILED", "REJECTED", "MANUALLY_REJECTED" -> State.FAILED;
      case "REVERSED" -> State.REVERSED;
      case "RECEIVED", "QUEUED", "PENDING", "APPROVAL_PENDING", "VALIDATION_PENDING" -> State.PENDING;
      default -> State.UNKNOWN;
    };
  }

  private static String message(State state) {
    return switch (state) {
      case SUCCESS -> "The sandbox provider confirmed a simulated transfer. No real money was sent.";
      case FAILED -> "The sandbox provider reports that the transfer failed.";
      case REVERSED -> "The sandbox provider reports that the transfer was reversed.";
      case PENDING -> "The sandbox transfer is awaiting confirmation.";
      default -> "The sandbox provider has not confirmed a final transfer outcome.";
    };
  }

  private static Outcome unknown(String message) {
    return new Outcome(State.UNKNOWN, null, null, null, null, message, null, null, null, null);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isString() ? value.asString() : null;
  }

  private static boolean present(JsonNode node, String field) {
    return !node.path(field).isMissingNode() && !node.path(field).isNull();
  }

  private static String field(JsonNode node, String field, String pattern) {
    String value = text(node, field);
    return value != null && value.matches(pattern) ? value : null;
  }

  private static BigDecimal amount(JsonNode node) {
    if (!node.isNumber()) return null;
    BigDecimal value = node.decimalValue();
    return value.signum() > 0 && value.stripTrailingZeros().scale() <= 2 ? value : null;
  }

  private static void validate(PayoutRequest payout) {
    if (payout == null) throw new IllegalArgumentException("A payout request is required");
    validateTransferId(payout.transferId());
    if (payout.bankAccountNumber() == null || !payout.bankAccountNumber().matches("[A-Za-z0-9]{9,18}")) {
      throw new IllegalArgumentException("The external bank account must contain 9 to 18 alphanumeric characters");
    }
    if (payout.ifsc() == null || !payout.ifsc().matches("[A-Z]{4}0[A-Z0-9]{6}")) {
      throw new IllegalArgumentException("A valid uppercase IFSC is required");
    }
    if (payout.beneficiaryName() == null || !payout.beneficiaryName().matches("[\\p{L}\\p{M} ]+")
        || payout.beneficiaryName().isBlank()
        || payout.beneficiaryName().codePointCount(0, payout.beneficiaryName().length()) > 100) {
      throw new IllegalArgumentException("The beneficiary name must contain 1 to 100 letters or spaces");
    }
    if (payout.amount() == null || payout.amount().compareTo(BigDecimal.ONE) < 0
        || payout.amount().stripTrailingZeros().scale() > 2) {
      throw new IllegalArgumentException("The payout amount must be at least INR 1 with at most two decimal places");
    }
  }

  private static void validateTransferId(String value) {
    if (value == null || !value.matches("[A-Za-z0-9_]{1,40}")) {
      throw new IllegalArgumentException("The transfer ID must contain 1 to 40 letters, numbers or underscores");
    }
  }

  private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription subscription;
    private long size;
    private boolean failed;

    @Override
    public CompletionStage<byte[]> getBody() { return delegate.getBody(); }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      delegate.onSubscribe(subscription);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      if (failed) return;
      for (ByteBuffer buffer : buffers) size += buffer.remaining();
      if (size > MAX_RESPONSE_BYTES) {
        failed = true;
        subscription.cancel();
        delegate.onError(new IOException("Provider response exceeded the allowed size"));
      } else {
        delegate.onNext(buffers);
      }
    }

    @Override
    public void onError(Throwable failure) { if (!failed) delegate.onError(failure); }

    @Override
    public void onComplete() { if (!failed) delegate.onComplete(); }
  }
}
