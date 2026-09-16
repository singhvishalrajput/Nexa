package com.nexa.api.conversations;

import com.nexa.api.identity.CurrentUserProvider;
import com.nexa.api.nlp.BankingLanguage;
import com.nexa.api.shared.errors.ResourceNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ConversationService {
  public record Conversation(String id, String title, OffsetDateTime createdAt) {}

  // Return sequence as a string so JavaScript does not lose integer precision.
  public record Turn(
      String id,
      String clientId,
      String source,
      String intent,
      String userText,
      String assistantText,
      OffsetDateTime createdAt,
      BankingContent banking,
      Workflow workflow) {
    public Turn(
        String id,
        String clientId,
        String source,
        String intent,
        String userText,
        String assistantText,
        OffsetDateTime createdAt,
        BankingContent banking) {
      this(id, clientId, source, intent, userText, assistantText, createdAt, banking, null);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("response")
    public Response response() {
      return new Response(
          banking == null ? "TEXT" : banking.envelopeType(),
          assistantText,
          banking,
          banking == null ? null : banking.errorCode(),
          banking == null ? null : banking.meta());
    }
  }

  public record Response(
      String type,
      String message,
      BankingContent data,
      String errorCode,
      java.util.Map<String, Object> meta) {}

  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final ConversationInterpreter interpreter;
  private final ObjectMapper json;
  private final WorkflowService workflows;

  public ConversationService(
      JdbcTemplate db,
      CurrentUserProvider user,
      ConversationInterpreter interpreter,
      ObjectMapper json,
      WorkflowService workflows) {
    this.db = db;
    this.user = user;
    this.interpreter = interpreter;
    this.json = json;
    this.workflows = workflows;
  }

  public Conversation create() {
    String id = UUID.randomUUID().toString();
    db.update("INSERT INTO conversations(id, user_id) VALUES (?, ?)", id, user.userId());
    return db.queryForObject("SELECT * FROM conversations WHERE id = ?", this::conversation, id);
  }

  @Transactional(readOnly = true)
  public List<Conversation> list(int page) {
    return db.query(
        "SELECT * FROM conversations WHERE user_id = ? ORDER BY created_at DESC, id DESC OFFSET ?"
            + " ROWS FETCH NEXT 30 ROWS ONLY",
        this::conversation,
        user.userId(),
        (long) page * 30);
  }

  private void requireOwned(String id, boolean lock) {
    var found =
        db.queryForList(
            "SELECT id FROM conversations WHERE id = ? AND user_id = ?"
                + (lock ? " FOR UPDATE" : ""),
            String.class,
            id,
            user.userId());
    if (found.isEmpty()) throw new ResourceNotFoundException("The conversation was not found.");
  }

  @Transactional(readOnly = true)
  public List<Turn> history(String id, long before) {
    requireOwned(id, false);
    return db.query(
        "SELECT * FROM conversation_turns WHERE conversation_id = ? AND sequence_id < ? ORDER BY"
            + " sequence_id DESC FETCH NEXT 30 ROWS ONLY",
        this::turn,
        id,
        before);
  }

  public Turn append(String id, String clientId, String source, String text) {
    return append(id, clientId, source, text, null);
  }

  public Turn append(
      String id, String clientId, String source, String text, Workflow.Command command) {
    // Serialize writers per conversation, including retry checks and deletion.
    requireOwned(id, true);
    var existing =
        db.query(
            "SELECT * FROM conversation_turns WHERE conversation_id = ? AND client_id = ?",
            this::turn,
            id,
            clientId);
    if (!existing.isEmpty()) return existing.get(0);
    long started = System.nanoTime();
    boolean sensitive =
        command == null
            && (text.matches(
                    "(?is).*\\b(password|passcode|pin|otp|cvv|bearer|secret|api.?key)\\b.*")
                || text.matches("(?s).*(?:\\d[ -]?){13,19}.*"));
    var workflow =
        sensitive
            ? null
            : workflows.handle(
                id, command == null ? resolveActionReference(id, text) : text, command);
    var interpretation =
        sensitive
            ? new ConversationInterpreter.Interpretation(
                "PRIVACY",
                "Sensitive message removed.",
                "Please do not send passwords, PINs, verification codes or full card numbers. This"
                    + " message was not retained. Describe what you need without those details.")
            : workflow == null
                ? interpretWithContext(id, text)
                : new ConversationInterpreter.Interpretation(
                    workflow.operation(), workflowTitle(workflow), workflow.message());
    if (!sensitive
        && workflow == null
        && java.util.Set.of("START_TRANSFER", "PAY_BILL", "PAY_CARD", "CANCEL_MANDATE")
            .contains(interpretation.intent())
        && !BankingLanguage.guarded(text)) {
      workflow = workflows.startRequested(id, text, interpretation.intent());
      if (workflow != null)
        interpretation =
            new ConversationInterpreter.Interpretation(
                workflow.operation(), workflowTitle(workflow), workflow.message());
    }
    String storedText =
        sensitive || "VOICE".equals(source) ? interpretation.essence() : text.trim();
    db.update(
        "INSERT INTO conversation_turns(conversation_id, client_id, source, intent, user_text,"
            + " assistant_text, banking_content, workflow_content) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        clientId,
        source,
        interpretation.intent(),
        storedText,
        interpretation.reply(),
        encode(interpretation.banking()),
        workflow == null ? null : json.writeValueAsString(workflow));
    org.slf4j.LoggerFactory.getLogger(getClass())
        .info(
            "Conversation turn user={} intent={} action={} durationMs={}",
            user.userId(),
            interpretation.intent(),
            workflow == null ? "none" : workflow.status(),
            (System.nanoTime() - started) / 1_000_000);
    // Derive titles from the safe essence, never the voice transcript.
    db.update(
        "UPDATE conversations SET title = ? WHERE id = ? AND title = 'New conversation'",
        interpretation.essence().substring(0, Math.min(120, interpretation.essence().length())),
        id);
    return db.queryForObject(
        "SELECT * FROM conversation_turns WHERE conversation_id = ? AND client_id = ?",
        this::turn,
        id,
        clientId);
  }

  public void delete(String id) {
    requireOwned(id, true);
    db.update("DELETE FROM conversations WHERE id = ? AND user_id = ?", id, user.userId());
  }

  private String workflowTitle(Workflow workflow) {
    return switch (workflow.operation()) {
      case "OWN_TRANSFER", "START_TRANSFER" -> "Money transfer";
      case "PAY_BILL" -> "Bill payment";
      case "PAY_CARD" -> "Card payment";
      case "CANCEL_MANDATE" -> "Direct debit cancellation";
      default -> "Card controls";
    };
  }

  private ConversationInterpreter.Interpretation interpretWithContext(String id, String text) {
    String normalized = BankingLanguage.normalize(text);
    var simpleRead = com.nexa.api.nlp.FastBankingIntent.match(text);
    if (simpleRead == com.nexa.api.nlp.Intent.GET_BALANCE
        || simpleRead == com.nexa.api.nlp.Intent.GET_RECENT_TRANSACTIONS) {
      var latest =
          db.query(
              "SELECT * FROM conversation_turns WHERE conversation_id = ? ORDER BY sequence_id DESC"
                  + " FETCH NEXT 1 ROWS ONLY",
              this::turn,
              id);
      if (!latest.isEmpty()
          && latest.get(0).banking() != null
          && !normalized.matches(".*\\b(all|savings|current)\\b.*")) {
        var content = latest.get(0).banking();
        String accountId =
            content.account() != null
                ? content.account().id()
                : content.accounts() != null && content.accounts().size() == 1
                    ? content.accounts().get(0).id()
                    : null;
        if (accountId != null) return interpreter.readForAccount(text, accountId);
      }
    }
    if (BankingLanguage.continuation(text)
        || normalized.matches(
            "(?s)^(only|just|what about|and for|for that|its|uska|last month|this month|from"
                + " |first|the first|details|savings|current).*")) {
      var latest =
          db.query(
              "SELECT * FROM conversation_turns WHERE conversation_id = ? ORDER BY sequence_id DESC"
                  + " FETCH NEXT 1 ROWS ONLY",
              this::turn,
              id);
      if (!latest.isEmpty() && latest.get(0).workflow() == null) {
        var previous = latest.get(0);
        var followup =
            interpreter.followUp(text, previous.intent(), previous.banking(), previous.userText());
        if (followup != null) return followup;
      }
      if (BankingLanguage.continuation(text))
        return new ConversationInterpreter.Interpretation(
            "UNKNOWN", "Clarify banking request.", "What would you like me to help with?");
    }
    if (!interpreter.usesLocalModel() || interpreter.isFastRequest(text))
      return interpreter.interpret(text);
    // append has already checked ownership and locked this conversation. Never include other chats.
    var recent =
        db.query(
            "SELECT * FROM conversation_turns WHERE conversation_id = ? ORDER BY sequence_id DESC"
                + " FETCH NEXT 4 ROWS ONLY",
            this::turn,
            id);
    var messages = new java.util.ArrayList<com.nexa.api.nlp.OllamaInterpreter.Message>();
    for (int i = recent.size() - 1; i >= 0; i--) {
      var turn = recent.get(i);
      if ("PRIVACY".equals(turn.intent())) continue;
      messages.add(new com.nexa.api.nlp.OllamaInterpreter.Message("user", turn.userText()));
      // No account/card payloads, credentials or database snapshots are sent to the model.
      messages.add(
          new com.nexa.api.nlp.OllamaInterpreter.Message("assistant", turn.assistantText()));
    }
    return interpreter.interpret(text, messages);
  }

  private String resolveActionReference(String id, String text) {
    String normalized = BankingLanguage.normalize(text);
    if (!normalized.matches(
            "(?:please )?(pay it|pay that|pay this(?: bill)?|pay the bill|bill pay karo|send"
                + " (?:him|her|them) .+)")
        || BankingLanguage.guarded(text)) return text;
    var latest =
        db.query(
            "SELECT * FROM conversation_turns WHERE conversation_id = ? ORDER BY sequence_id DESC"
                + " FETCH NEXT 1 ROWS ONLY",
            this::turn,
            id);
    if (latest.isEmpty() || latest.get(0).banking() == null) return text;
    var content = latest.get(0).banking();
    if (content.bills() != null)
      return "pay bill " + (content.bills().size() == 1 ? content.bills().get(0).id() : "");
    if (content.cards() != null)
      return "pay card " + (content.cards().size() == 1 ? content.cards().get(0).id() : "");
    if (content.beneficiaries() != null && content.beneficiaries().size() == 1)
      return normalized + " to " + content.beneficiaries().get(0).id();
    return text;
  }

  private Conversation conversation(ResultSet row, int ignored) throws SQLException {
    return new Conversation(
        row.getString("id"),
        row.getString("title"),
        row.getObject("created_at", OffsetDateTime.class));
  }

  private Turn turn(ResultSet row, int ignored) throws SQLException {
    return new Turn(
        row.getString("sequence_id"),
        row.getString("client_id"),
        row.getString("source"),
        row.getString("intent"),
        row.getString("user_text"),
        row.getString("assistant_text"),
        row.getObject("created_at", OffsetDateTime.class),
        decode(row.getString("banking_content")),
        row.getString("workflow_content") == null
            ? null
            : json.readValue(row.getString("workflow_content"), Workflow.class));
  }

  private String encode(BankingContent content) {
    if (content == null) return null;
    try {
      return json.writeValueAsString(content);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to encode banking content.");
    }
  }

  private BankingContent decode(String content) throws SQLException {
    if (content == null) return null;
    try {
      return json.readValue(content, BankingContent.class);
    } catch (JacksonException exception) {
      throw new SQLException("Unable to read banking content.");
    }
  }
}
