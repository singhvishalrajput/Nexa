package com.nexa.api.conversations;

import com.nexa.api.identity.CurrentUserProvider;
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
    var workflow = sensitive ? null : workflows.handle(id, text, command);
    var interpretation =
        sensitive
            ? new ConversationInterpreter.Interpretation(
                "PRIVACY",
                "Sensitive message removed.",
                "Please do not send passwords, PINs, verification codes or full card numbers. This"
                    + " message was not retained. Describe what you need without those details.")
            : workflow == null
                ? interpreter.interpret(text)
                : new ConversationInterpreter.Interpretation(
                    workflow.operation(), "Banking request.", workflow.message());
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
