package com.nexa.api.nlp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexa.api.conversations.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

class OllamaInterpreterTest {
  private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  private HttpServer server;
  private final AtomicReference<String> request = new AtomicReference<>();

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  private OllamaInterpreter stub(String content, int status) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/chat",
        exchange -> {
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes =
              json.writeValueAsBytes(Map.of("done", true, "message", Map.of("content", content)));
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return new OllamaInterpreter(
        json, true, "http://127.0.0.1:" + server.getAddress().getPort(), "test", 2);
  }

  private String plan(String intent, String id, String from) {
    return json.writeValueAsString(
        new OllamaInterpreter.Plan(intent, id, null, null, from, null, null, null, null, null));
  }

  @Test
  void usesStructuredLocalRequestAndHistory() throws Exception {
    var ai = stub(plan("GET_BALANCE", null, null), 200);
    assertThat(
            ai.interpret(
                    "and savings?", List.of(new OllamaInterpreter.Message("user", "show balances")))
                .intent())
        .isEqualTo("GET_BALANCE");
    var body = json.readTree(request.get());
    assertThat(body.path("think").asBoolean()).isFalse();
    assertThat(body.path("stream").asBoolean()).isFalse();
    assertThat(body.path("format").path("additionalProperties").asBoolean()).isFalse();
    assertThat(body.path("messages").size()).isEqualTo(3);
  }

  @Test
  void rejectsInventedAccountAndInvalidDates() throws Exception {
    var ai = stub(plan("GET_BALANCE", "999", null), 200);
    assertThat(ai.interpret("my balance", List.of())).isNull();
    server.stop(0);
    ai = stub(plan("GET_RECENT_TRANSACTIONS", null, "2026-99-99"), 200);
    assertThat(ai.interpret("my payments", List.of())).isNull();
  }

  @Test
  void malformedOutputAndServerFailureFallBack() throws Exception {
    var ai = stub("not json", 200);
    assertThat(ai.interpret("hello", List.of())).isNull();
    request.set(null);
    assertThat(ai.interpret("hello", List.of())).isNull();
    assertThat(request.get()).isNull(); // backoff avoids repeated blocking calls
    server.stop(0);
    ai = stub("{}", 503);
    assertThat(ai.interpret("hello", List.of())).isNull();
  }

  @Test
  void modelActionCannotReachDomainWriteOrPreparation() throws Exception {
    var ai = stub(plan("START_TRANSFER", null, null), 200);
    var router = mock(DomainRouter.class);
    var interpreter =
        new ConversationInterpreter(
            mock(IntentClassifier.class), mock(EntityExtractor.class), router, false);
    interpreter.setOllama(ai);
    assertThat(interpreter.interpret("move some money").reply())
        .isEqualTo("Who would you like to pay?");
    verifyNoInteractions(router);
  }

  @Test
  void modelReadUsesRealDomainPayload() throws Exception {
    var ai = stub(plan("GET_BALANCE", null, null), 200);
    var router = mock(DomainRouter.class);
    var payload = BankingContent.balances(List.of());
    when(router.route(eq(Intent.GET_BALANCE), any()))
        .thenReturn(new DomainRouter.Reply("ACCOUNT_LIST", "Actual data", payload, null));
    var interpreter =
        new ConversationInterpreter(
            mock(IntentClassifier.class), mock(EntityExtractor.class), router, false);
    interpreter.setOllama(ai);
    var result = interpreter.interpret("kitne paise hain?");
    assertThat(result.reply()).isEqualTo("Actual data");
    assertThat(result.banking().type()).isEqualTo(payload.type());
    verify(router).route(eq(Intent.GET_BALANCE), any());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "NEXA_OLLAMA_SMOKE", matches = "true")
  void liveLocalModelUnderstandsParaphrasesAndFollowups() {
    var ai = new OllamaInterpreter(json, true, "http://localhost:11434", "qwen3.5:4b", 60);
    var balance = ai.interpret("mere account mein kitne paise hain?", List.of());
    assertThat(balance).isNotNull();
    assertThat(balance.intent()).isEqualTo("GET_BALANCE");
    var followup =
        ai.interpret(
            "only the savings one",
            List.of(
                new OllamaInterpreter.Message("user", "show my balances"),
                new OllamaInterpreter.Message("assistant", "Here are your available balances.")));
    assertThat(followup).isNotNull();
    assertThat(followup.intent()).isIn("GET_BALANCE", "GET_ACCOUNTS");
    assertThat(followup.accountType()).isEqualTo("SAVINGS");
    var cancelled = ai.interpret("Don't send 500 to Rahul", List.of());
    assertThat(cancelled).isNotNull();
    assertThat(cancelled.intent()).isEqualTo("UNKNOWN");
  }
}
