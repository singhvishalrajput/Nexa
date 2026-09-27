package com.nexa.api.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.nexa.api.external.ExternalPayoutProvider.PayoutRequest;
import com.nexa.api.external.ExternalPayoutProvider.State;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** Uses an actual loopback HTTP server. It never calls a Cashfree environment or reads credentials. */
class CashfreePayoutProviderTest {
  private static final String TRANSFER = "EX01234567890123456789012345678901";
  private static final String ACCOUNT = "001111222333";
  private static final String IFSC = "HDFC0000001";
  private final JsonMapper json = JsonMapper.builder().build();
  private final AtomicReference<Reply> reply = new AtomicReference<>();
  private final AtomicReference<Received> received = new AtomicReference<>();
  private final AtomicInteger requests = new AtomicInteger();
  private final AtomicInteger redirected = new AtomicInteger();
  private final CountDownLatch releaseResponse = new CountDownLatch(1);
  private HttpServer server;
  private ExecutorService workers;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    workers = Executors.newFixedThreadPool(2);
    server.setExecutor(workers);
    reply.set(new Reply(200, receipt("RECEIVED", "RECEIVED"), false));
    server.createContext("/payout/transfers", exchange -> {
      requests.incrementAndGet();
      received.set(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getRawQuery(),
          exchange.getRequestHeaders().getFirst("x-client-id"),
          exchange.getRequestHeaders().getFirst("x-client-secret"),
          exchange.getRequestHeaders().getFirst("x-api-version"),
          exchange.getRequestHeaders().getFirst("Content-Type"),
          new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
      try {
        Reply response = reply.get();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (response.status() == 307) {
          exchange.getResponseHeaders().set("Location", endpoint().resolve("/redirected").toString());
        }
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), bytes.length);
        if (response.stallBody()) releaseResponse.await(5, TimeUnit.SECONDS);
        exchange.getResponseBody().write(bytes);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      } finally {
        exchange.close();
      }
    });
    server.createContext("/redirected", exchange -> {
      redirected.incrementAndGet();
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stopServer() {
    releaseResponse.countDown();
    if (server != null) server.stop(0);
    if (workers != null) workers.shutdownNow();
  }

  @Test
  void sendsDocumentedDirectBankContractAndRetainsExactDecimalAndReference() {
    var outcome = provider().submit(payout());
    assertThat(outcome.state()).isEqualTo(State.PENDING);
    var request = received.get();
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.query()).isNull();
    assertThat(request.clientId()).isEqualTo("test-client");
    assertThat(request.clientSecret()).isEqualTo("test-secret");
    assertThat(request.version()).isEqualTo("2024-01-01");
    assertThat(request.contentType()).isEqualTo("application/json");
    var body = json.readTree(request.body());
    assertThat(body.path("transfer_id").asText()).isEqualTo(TRANSFER);
    assertThat(body.path("transfer_amount").decimalValue()).isEqualByComparingTo("123.45");
    assertThat(body.path("transfer_currency").asText()).isEqualTo("INR");
    assertThat(body.path("transfer_mode").asText()).isEqualTo("imps");
    assertThat(body.path("beneficiary_details").path("beneficiary_name").asText()).isEqualTo("Test Recipient");
    var instrument = body.path("beneficiary_details").path("beneficiary_instrument_details");
    assertThat(instrument.path("bank_account_number").asText()).isEqualTo(ACCOUNT);
    assertThat(instrument.path("bank_ifsc").asText()).isEqualTo(IFSC);
    assertThat(body.path("beneficiary_details").has("beneficiary_id")).isFalse();
    assertThat(requests).hasValue(1);
  }

  @ParameterizedTest
  @CsvSource({
      "RECEIVED,RECEIVED,PENDING", "QUEUED,QUEUED,PENDING", "PENDING,REQUEST_TIMEDOUT,PENDING",
      "PENDING,DUPLICATE,PENDING", "APPROVAL_PENDING,VELOCITY_CHECK_FAILED,PENDING",
      "VALIDATION_PENDING,VALIDATION_PENDING,PENDING", "REJECTED,DUPLICATE_TRANSFER,UNKNOWN",
      "SUCCESS,SENT_TO_BENEFICIARY,PENDING", "SUCCESS,COMPLETED,SUCCESS",
      "SUCCESS,NEW_CODE,PENDING", "FAILED,INVALID_IFSC_FAIL,FAILED", "REJECTED,BENE_NOT_EXIST,FAILED",
      "MANUALLY_REJECTED,MANUALLY_REJECTED,FAILED", "REVERSED,RETURNED_FROM_BENEFICIARY,REVERSED",
      "NEW_STATUS,NEW_CODE,UNKNOWN"
  })
  void mapsDocumentedStatesWithoutConfusingAcceptanceAndCredit(String status, String code, State expected) {
    reply.set(new Reply(200, receipt(status, code), false));
    var outcome = provider().status(TRANSFER);
    assertThat(outcome.state()).isEqualTo(expected);
    assertThat(outcome.transferId()).isEqualTo(TRANSFER);
    assertThat(outcome.providerTransferId()).isEqualTo("123456");
    assertThat(outcome.amount()).isEqualByComparingTo("123.45");
    assertThat(outcome.bankAccountNumber()).isEqualTo(ACCOUNT);
    assertThat(outcome.ifsc()).isEqualTo(IFSC);
    assertThat(outcome.utr()).isEqualTo("UTR123456");
    assertThat(outcome.message()).containsIgnoringCase("sandbox").doesNotContain(ACCOUNT, "test-secret");
    assertThat(received.get().method()).isEqualTo("GET");
    assertThat(received.get().query()).isEqualTo("transfer_id=" + TRANSFER);
    assertThat(received.get().body()).isEmpty();
    assertThat(requests).hasValue(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 409, 429, 500, 502, 503})
  void postErrorsNeverImplyFailureOrResubmit(int status) {
    reply.set(new Reply(status, "{\"code\":\"transfer_id_already_exists\",\"message\":\""
        + ACCOUNT + " test-secret\"}", false));
    var outcome = provider().submit(payout());
    assertThat(outcome.state()).isEqualTo(State.UNKNOWN);
    assertThat(outcome.message()).doesNotContain(ACCOUNT, "test-secret");
    assertThat(requests).hasValue(1);
  }

  @Test
  void uncertainPostCanBeRecoveredByGetWithTheSameReference() {
    var provider = provider();
    reply.set(new Reply(503, "unavailable", false));
    assertThat(provider.submit(payout()).state()).isEqualTo(State.UNKNOWN);
    reply.set(new Reply(200, receipt("SUCCESS", "COMPLETED"), false));
    assertThat(provider.status(TRANSFER).state()).isEqualTo(State.SUCCESS);
    assertThat(requests).hasValue(2);
    assertThat(received.get().method()).isEqualTo("GET");
    assertThat(received.get().query()).isEqualTo("transfer_id=" + TRANSFER);
  }

  @Test
  void preservesUnicodeBeneficiaryLettersAndMarksInTheRequest() {
    String name = "पूर्वा देशमुख";
    provider().submit(new PayoutRequest(TRANSFER, ACCOUNT, IFSC, name, BigDecimal.TEN));
    assertThat(json.readTree(received.get().body()).path("beneficiary_details")
        .path("beneficiary_name").asText()).isEqualTo(name);
  }

  @Test
  void successCanLaterBeObservedAsReversed() {
    var provider = provider();
    reply.set(new Reply(200, receipt("SUCCESS", "COMPLETED"), false));
    assertThat(provider.status(TRANSFER).state()).isEqualTo(State.SUCCESS);
    reply.set(new Reply(200, receipt("REVERSED", "RETURNED_FROM_BENEFICIARY"), false));
    assertThat(provider.status(TRANSFER).state()).isEqualTo(State.REVERSED);
  }

  @Test
  void getNotFoundIsOnlyAStructuredProviderObservation() {
    var provider = provider();
    reply.set(new Reply(404, "{\"code\":\"transfer_not_found\"}", false));
    assertThat(provider.status(TRANSFER).state()).isEqualTo(State.NOT_FOUND);
    reply.set(new Reply(404, "<html>Not found</html>", false));
    assertThat(provider.status(TRANSFER).state()).isEqualTo(State.UNKNOWN);
    reply.set(new Reply(404, "{\"code\":\"beneficiary_not_found\"}", false));
    assertThat(provider.status(TRANSFER).state()).isEqualTo(State.UNKNOWN);
  }

  @Test
  void missingOptionalReceiptDetailsAreNotInvented() {
    reply.set(new Reply(200, json.writeValueAsString(Map.of("transfer_id", TRANSFER,
        "cf_transfer_id", "123456", "status", "SUCCESS", "status_code", "COMPLETED")), false));
    var outcome = provider().status(TRANSFER);
    assertThat(outcome.state()).isEqualTo(State.SUCCESS);
    assertThat(outcome.amount()).isNull();
    assertThat(outcome.bankAccountNumber()).isNull();
    assertThat(outcome.ifsc()).isNull();
  }

  @Test
  void supportsBankIfscResponseAliasWithoutLosingConflictingCorrelation() {
    reply.set(new Reply(200, receipt("SUCCESS", "COMPLETED").replace("\"ifsc\"", "\"bank_ifsc\""), false));
    assertThat(provider().status(TRANSFER).ifsc()).isEqualTo(IFSC);
    reply.set(new Reply(200, receipt("SUCCESS", "COMPLETED").replace("\"ifsc\":", "\"bank_ifsc\":\"SBIN0000001\",\"ifsc\":"), false));
    assertThat(provider().status(TRANSFER).state()).isEqualTo(State.UNKNOWN);
  }

  @Test
  void malformedAndMismatchedReceiptsStayUnknownWithoutLeakingPayload() {
    var provider = provider();
    for (String body : new String[] {
        "not json " + ACCOUNT + " test-secret", "{}", "null",
        receipt("SUCCESS", "COMPLETED").replace(TRANSFER, "EX_DIFFERENT"),
        receipt("SUCCESS", "COMPLETED").replace("123.45", "\"invalid\""),
        receipt("SUCCESS", "COMPLETED").replace(ACCOUNT, "***3333"),
        receipt("SUCCESS", "COMPLETED").replace(IFSC, "INVALID_IFSC")
    }) {
      reply.set(new Reply(200, body, false));
      var outcome = provider.status(TRANSFER);
      assertThat(outcome.state()).isEqualTo(State.UNKNOWN);
      assertThat(outcome.message()).doesNotContain(ACCOUNT, "test-secret");
    }
  }

  @Test
  void credentialsAreNeverFollowedAcrossRedirects() {
    reply.set(new Reply(307, "{}", false));
    assertThat(provider().submit(payout()).state()).isEqualTo(State.UNKNOWN);
    assertThat(requests).hasValue(1);
    assertThat(redirected).hasValue(0);
  }

  @Test
  void oversizedProviderBodyIsRejected() {
    reply.set(new Reply(200, " ".repeat(CashfreePayoutProvider.MAX_RESPONSE_BYTES + 1)
        + receipt("SUCCESS", "COMPLETED"), false));
    assertThat(provider().status(TRANSFER).state()).isEqualTo(State.UNKNOWN);
  }

  @Test
  void bodyTimeoutIsBoundedAndNeverRetriesThePost() {
    reply.set(new Reply(200, receipt("SUCCESS", "COMPLETED"), true));
    var provider = new CashfreePayoutProvider(json,
        new PayoutProperties("SANDBOX", "test-client", "test-secret", 1), endpoint());
    long start = System.nanoTime();
    assertThat(provider.submit(payout()).state()).isEqualTo(State.UNKNOWN);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
    assertThat(requests).hasValue(1);
  }

  @Test
  void disabledLiveAndMissingCredentialsNeverMakeNetworkRequests() {
    for (PayoutProperties properties : new PayoutProperties[] {
        new PayoutProperties("DISABLED", "test-client", "test-secret", 2),
        new PayoutProperties("LIVE", "test-client", "test-secret", 2),
        new PayoutProperties("SANDBOX", "", "", 2),
        new PayoutProperties("SANDBOX", "test-client", "injected\r\nheader", 2)
    }) {
      var provider = new CashfreePayoutProvider(json, properties, endpoint());
      assertThat(provider.available()).isFalse();
      assertThat(provider.unavailableReason()).isNotBlank();
      assertThat(provider.submit(payout()).state()).isEqualTo(State.UNKNOWN);
      assertThat(provider.status(TRANSFER).state()).isEqualTo(State.UNKNOWN);
      assertThat(properties.toString()).doesNotContain("test-secret", "injected");
    }
    assertThat(requests).hasValue(0);
  }

  @Test
  void invalidRequestAndUnsafeConfigurationAreRejectedBeforeNetworkAccess() {
    var provider = provider();
    assertThatIllegalArgumentException().isThrownBy(() -> provider.status("EX123&other=value"));
    assertThatIllegalArgumentException().isThrownBy(() -> provider.submit(
        new PayoutRequest(TRANSFER, ACCOUNT, IFSC, "Test Recipient", new BigDecimal("1.001"))));
    assertThatIllegalArgumentException().isThrownBy(() -> provider.submit(
        new PayoutRequest(TRANSFER, ACCOUNT, "invalid", "Test Recipient", BigDecimal.TEN)));
    assertThatIllegalArgumentException().isThrownBy(() -> provider.submit(
        new PayoutRequest(TRANSFER, "123", IFSC, "Test Recipient", BigDecimal.TEN)));
    assertThatIllegalArgumentException().isThrownBy(() -> provider.submit(
        new PayoutRequest(TRANSFER, ACCOUNT, IFSC, "Recipient<script>", BigDecimal.TEN)));
    assertThatIllegalArgumentException().isThrownBy(() -> new PayoutProperties("unexpected", "", "", 2));
    assertThatIllegalArgumentException().isThrownBy(() -> new PayoutProperties("SANDBOX", "", "", 0));
    assertThatIllegalArgumentException().isThrownBy(() -> new CashfreePayoutProvider(json,
        new PayoutProperties("SANDBOX", "test-client", "test-secret", 2),
        URI.create("https://api.cashfree.com/payout/transfers")));
    assertThat(requests).hasValue(0);
    assertThat(payout().toString()).doesNotContain(ACCOUNT, "Test Recipient");
  }

  private CashfreePayoutProvider provider() {
    return new CashfreePayoutProvider(json,
        new PayoutProperties("SANDBOX", "test-client", "test-secret", 2), endpoint());
  }

  private URI endpoint() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/payout/transfers");
  }

  private PayoutRequest payout() {
    return new PayoutRequest(TRANSFER, ACCOUNT, IFSC, "Test Recipient", new BigDecimal("123.45"));
  }

  private String receipt(String status, String code) {
    return json.writeValueAsString(Map.of("transfer_id", TRANSFER, "cf_transfer_id", "123456",
        "status", status, "status_code", code, "transfer_amount", new BigDecimal("123.45"),
        "transfer_utr", "UTR123456", "status_description", "provider detail containing " + ACCOUNT,
        "beneficiary_details", Map.of("beneficiary_instrument_details",
            Map.of("bank_account_number", ACCOUNT, "ifsc", IFSC))));
  }

  private record Reply(int status, String body, boolean stallBody) {}
  private record Received(String method, String query, String clientId, String clientSecret,
      String version, String contentType, String body) {}
}
