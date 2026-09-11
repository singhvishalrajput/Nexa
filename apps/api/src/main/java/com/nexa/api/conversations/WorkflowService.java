package com.nexa.api.conversations;

import com.nexa.api.accounts.AccountQueryService;
import com.nexa.api.banking.*;
import com.nexa.api.beneficiaries.BeneficiaryQueryService;
import com.nexa.api.nlp.*;
import com.nexa.api.shared.errors.InvalidRequestException;
import com.nexa.api.shared.errors.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Invoked under the owning conversation's transaction and row lock. No text authorizes a write. */
@Service
public class WorkflowService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final IntentClassifier classifier;
  private final EntityExtractor extractor;
  private final AccountQueryService accounts;
  private final BeneficiaryQueryService beneficiaries;
  private final BillQueryService bills;
  private final ActionPreparationService preparation;
  private final CustomerTransferService transfers;
  private final com.nexa.api.identity.CurrentUserProvider user;

  public WorkflowService(
      JdbcTemplate db,
      ObjectMapper json,
      IntentClassifier classifier,
      EntityExtractor extractor,
      AccountQueryService accounts,
      BeneficiaryQueryService beneficiaries,
      BillQueryService bills,
      ActionPreparationService preparation,
      CustomerTransferService transfers,
      com.nexa.api.identity.CurrentUserProvider user) {
    this.db = db;
    this.json = json;
    this.classifier = classifier;
    this.extractor = extractor;
    this.accounts = accounts;
    this.beneficiaries = beneficiaries;
    this.bills = bills;
    this.preparation = preparation;
    this.transfers = transfers;
    this.user = user;
  }

  public Workflow handle(String conversation, String text, Workflow.Command command) {
    var rows =
        db.query(
            "SELECT state FROM conversation_workflows WHERE conversation_id = ? ORDER BY updated_at"
                + " DESC, id DESC FETCH NEXT 1 ROWS ONLY",
            (r, n) -> json.readValue(r.getString(1), Workflow.class),
            conversation);
    Workflow old = rows.isEmpty() ? null : rows.get(0);
    if (command != null && (old == null || !old.id().equals(command.actionId())))
      throw new InvalidRequestException(
          "This action is no longer current. Open the latest proposal.");
    if (old != null && Set.of("COLLECTING", "REVIEW").contains(old.status())) {
      if (OffsetDateTime.now().isAfter(old.expiresAt())) {
        return save(
            conversation,
            copy(
                old,
                "EXPIRED",
                null,
                "This proposal expired. Start a new request.",
                List.of(),
                null));
      }
      if (command != null && "CANCEL".equals(command.type())
          || text.trim().equalsIgnoreCase("cancel"))
        return save(
            conversation,
            copy(
                old,
                "CANCELLED",
                null,
                "Cancelled. No banking operation was executed.",
                List.of(),
                null));
      if ("REVIEW".equals(old.status())) {
        if (command != null && "CONFIRM".equals(command.type())) {
          // Only the explicit, action-bound API command reaches the domain write capability.
          if (!old.executionAvailable())
            throw new InvalidRequestException("Execution is unavailable for this action.");
          String reference =
              transfers.execute(old.accountId(), old.targetId(), new BigDecimal(old.amount()));
          return save(
              conversation,
              copy(
                  old,
                  "COMPLETED",
                  null,
                  "Your transfer was completed successfully.",
                  List.of(),
                  reference));
        }
        if (command != null)
          throw new InvalidRequestException("Use Confirm transfer or Cancel on the proposal.");
        if (text.trim().matches("(?i)(yes|confirm|proceed|ok|okay)[.!]?")) return old;
        // Read questions may interrupt a proposal without replacing or authorizing it.
        return null;
      }
      if (command != null && !"SELECT".equals(command.type()))
        throw new InvalidRequestException("Complete the payment details before confirming.");
      if (command == null && classifier.classify(text).intent() != Intent.UNKNOWN) return null;
      return advance(conversation, old, command == null ? text.trim() : command.value());
    }
    if (command != null) {
      // Replayed confirmation returns the durable outcome, never another posting.
      if (old != null && "COMPLETED".equals(old.status()) && "CONFIRM".equals(command.type()))
        return old;
      throw new InvalidRequestException("This action is closed. Start a new request.");
    }
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.contains("safely") && lower.contains("transfer")) return null;
    if (lower.matches(".*\\b(freeze|unfreeze|block)\\b.*\\bcard\\b.*"))
      return new Workflow(
          1,
          UUID.randomUUID().toString(),
          "CARD_CONTROL",
          "UNAVAILABLE",
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          "Card controls are not connected. Open Cards for your recorded status and contact your"
              + " bank to freeze or unfreeze your card.",
          List.of(),
          OffsetDateTime.now(),
          false,
          false,
          null);
    var intent = classifier.classify(text).intent();
    boolean own =
        lower.matches(
                ".*\\b(transfer|move)\\b.*\\b(my accounts|own accounts|between accounts)\\b.*")
            && !lower.matches(".*\\b(not|don't|never|if|unless|and|then|tomorrow|every)\\b.*");
    if (!own && intent != Intent.START_TRANSFER && intent != Intent.PAY_BILL) return null;
    var entities = extractor.extract(text, own ? Intent.START_TRANSFER : intent);
    String operation = own ? "OWN_TRANSFER" : intent.name();
    String amount = entities.amount() == null ? null : entities.amount().toPlainString();
    var initial =
        new Workflow(
            1,
            UUID.randomUUID().toString(),
            operation,
            "COLLECTING",
            "target",
            null,
            null,
            null,
            null,
            amount,
            null,
            "Who would you like to pay?",
            List.of(),
            OffsetDateTime.now().plusMinutes(10),
            true,
            own,
            null);
    initial = withChoices(initial, "target", targets(initial));
    save(conversation, initial);
    if (entities.payee() != null)
      return advance(conversation, initial, entities.payee().replaceAll("[.!?]+$", ""));
    if (entities.targetId() != null) return advance(conversation, initial, entities.targetId());
    return initial;
  }

  private List<Workflow.Choice> targets(Workflow w) {
    return switch (w.operation()) {
      case "OWN_TRANSFER" ->
          accounts.currentAccounts().stream()
              .filter(a -> "ACTIVE".equals(a.status()))
              .map(
                  a ->
                      new Workflow.Choice(
                          a.id(), a.displayName() + " · " + a.accountNumberMasked()))
              .toList();
      case "PAY_BILL" ->
          bills.list(null, 0, 100).stream()
              .filter(b -> Set.of("DUE", "UPCOMING", "OVERDUE", "FAILED").contains(b.status()))
              .map(
                  b ->
                      new Workflow.Choice(
                          b.id(), b.billerName() + " · " + b.currencyCode() + " " + b.amount()))
              .toList();
      default ->
          beneficiaries.list().stream()
              .filter(b -> "ACTIVE".equals(b.status()))
              .map(
                  b ->
                      new Workflow.Choice(
                          b.id(),
                          b.displayName() + " · " + b.bankName() + " · " + b.accountNumberMasked()))
              .toList();
    };
  }

  private Workflow advance(String conversation, Workflow w, String value) {
    if (value == null || value.isBlank()) return w;
    String account = w.accountId(),
        target = w.targetId(),
        accountLabel = w.accountLabel(),
        targetLabel = w.targetLabel(),
        amount = w.amount();
    if ("amount".equals(w.field())) {
      if (!value.matches(
          "(?:₹|INR\\s*)?(?:[0-9]{1,13}|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?"))
        return save(
            conversation,
            copy(
                w,
                "COLLECTING",
                "amount",
                "Enter a positive amount, for example 5000 or 5000.50.",
                List.of(),
                null));
      amount = value.replaceAll("₹|INR|,|\\s", "");
      if (new BigDecimal(amount).signum() <= 0)
        return save(
            conversation,
            copy(
                w,
                "COLLECTING",
                "amount",
                "The amount must be greater than zero.",
                List.of(),
                null));
    } else {
      var choices = w.choices();
      var exact =
          choices.stream()
              .filter(c -> c.id().equals(value) || c.label().equalsIgnoreCase(value))
              .toList();
      var matches =
          exact.isEmpty()
              ? choices.stream()
                  .filter(
                      c ->
                          c.label()
                              .toLowerCase(Locale.ROOT)
                              .startsWith(value.toLowerCase(Locale.ROOT)))
                  .toList()
              : exact;
      if (matches.size() != 1)
        return save(
            conversation,
            copy(
                w,
                "COLLECTING",
                w.field(),
                matches.isEmpty()
                    ? "No matching active item. Choose one below."
                    : "I found more than one match. Choose the exact item below.",
                choices,
                null));
      if ("target".equals(w.field())) {
        target = matches.get(0).id();
        targetLabel = matches.get(0).label();
      } else {
        account = matches.get(0).id();
        accountLabel = matches.get(0).label();
      }
    }
    var next =
        new Workflow(
            1,
            w.id(),
            w.operation(),
            "COLLECTING",
            null,
            account,
            target,
            accountLabel,
            targetLabel,
            amount,
            null,
            "",
            List.of(),
            w.expiresAt(),
            true,
            w.executionAvailable(),
            null);
    if (account == null) {
      final String selected = target;
      var choices =
          accounts.currentAccounts().stream()
              .filter(
                  a ->
                      "ACTIVE".equals(a.status())
                          && (!w.operation().equals("OWN_TRANSFER") || !a.id().equals(selected)))
              .map(
                  a ->
                      new Workflow.Choice(
                          a.id(), a.displayName() + " · " + a.accountNumberMasked()))
              .toList();
      return save(conversation, withChoices(next, "account", choices));
    }
    if (amount == null && w.operation().equals("PAY_BILL")) amount = bills.detail(target).amount();
    if (amount == null) return save(conversation, withChoices(next, "amount", List.of()));
    try {
      if (w.operation().equals("OWN_TRANSFER"))
        transfers.validate(account, target, new BigDecimal(amount));
      else preparation.prepare(w.operation(), account, target, new BigDecimal(amount));
    } catch (InvalidRequestException | ResourceNotFoundException ex) {
      return save(
          conversation,
          copy(
              next,
              "COLLECTING",
              "amount",
              ex.getMessage() + " Enter another amount, or cancel to change accounts.",
              List.of(),
              null));
    }
    return save(
        conversation,
        new Workflow(
            1,
            w.id(),
            w.operation(),
            w.executionAvailable() ? "REVIEW" : "UNAVAILABLE",
            null,
            account,
            target,
            accountLabel,
            targetLabel,
            amount,
            accounts.requireOwnedAccount(account).currencyCode(),
            w.executionAvailable()
                ? "Review the details below. Use Confirm transfer to authorize this exact transfer."
                : "Details validated for review. This payment provider is not connected; no money"
                      + " has moved.",
            List.of(),
            w.expiresAt(),
            true,
            w.executionAvailable(),
            null));
  }

  private Workflow withChoices(Workflow w, String field, List<Workflow.Choice> choices) {
    String message =
        switch (field) {
          case "target" ->
              w.operation().equals("OWN_TRANSFER")
                  ? "Which account should receive the money?"
                  : w.operation().equals("PAY_BILL")
                      ? "Which bill would you like to pay?"
                      : "Who would you like to pay?";
          case "account" -> "Which account should the money come from?";
          default -> "How much would you like to transfer?";
        };
    if (!field.equals("amount") && choices.isEmpty())
      return copy(
          w,
          "UNAVAILABLE",
          null,
          "There are no eligible items for this request. Open your banking details to check them.",
          choices,
          null);
    return copy(w, "COLLECTING", field, message, choices, null);
  }

  private Workflow copy(
      Workflow w,
      String status,
      String field,
      String message,
      List<Workflow.Choice> choices,
      String reference) {
    return new Workflow(
        1,
        w.id(),
        w.operation(),
        status,
        field,
        w.accountId(),
        w.targetId(),
        w.accountLabel(),
        w.targetLabel(),
        w.amount(),
        w.currency(),
        message,
        choices,
        w.expiresAt(),
        w.confirmationRequired(),
        w.executionAvailable(),
        reference);
  }

  private Workflow save(String conversation, Workflow w) {
    String state = json.writeValueAsString(w);
    if (db.update(
            "UPDATE conversation_workflows SET state = ?, updated_at = CURRENT_TIMESTAMP WHERE id ="
                + " ? AND conversation_id = ?",
            state,
            w.id(),
            conversation)
        == 0)
      db.update(
          "INSERT INTO conversation_workflows(id, conversation_id, state) VALUES (?, ?, ?)",
          w.id(),
          conversation,
          state);
    db.update(
        "INSERT INTO"
            + " conversation_action_events(id,action_id,user_id,operation,status,source_account_id,target_id,amount,transaction_reference,correlation_id)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        w.id(),
        user.userId(),
        w.operation(),
        w.status(),
        w.accountId(),
        w.targetId(),
        w.amount(),
        w.reference(),
        org.slf4j.MDC.get("correlationId"));
    org.slf4j.LoggerFactory.getLogger(getClass())
        .info(
            "Conversation capability action={} operation={} state={}",
            w.id(),
            w.operation(),
            w.status());
    return w;
  }
}
