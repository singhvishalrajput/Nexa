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
  private final ShowcaseService showcase;
  private final CardQueryService cards;
  private final MandateQueryService mandates;
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
      ShowcaseService showcase,
      CardQueryService cards,
      MandateQueryService mandates,
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
    this.showcase = showcase;
    this.cards = cards;
    this.mandates = mandates;
    this.user = user;
  }

  public Workflow handle(String conversation, String text, Workflow.Command command) {
    // Turn sequence is monotonic. Timestamps can tie when a correction closes one action and
    // opens another in the same transaction, so UUID ordering cannot identify the current action.
    var snapshots =
        db.query(
            "SELECT workflow_content FROM conversation_turns WHERE conversation_id=? AND"
                + " workflow_content IS NOT NULL ORDER BY sequence_id DESC FETCH NEXT 1 ROWS ONLY",
            (r, n) -> json.readValue(r.getString(1), Workflow.class),
            conversation);
    var rows =
        snapshots.isEmpty()
            ? db.query(
                "SELECT state FROM conversation_workflows WHERE conversation_id = ? ORDER BY"
                    + " updated_at DESC, id DESC FETCH NEXT 1 ROWS ONLY",
                (r, n) -> json.readValue(r.getString(1), Workflow.class),
                conversation)
            : db.query(
                "SELECT state FROM conversation_workflows WHERE conversation_id=? AND id=?",
                (r, n) -> json.readValue(r.getString(1), Workflow.class),
                conversation,
                snapshots.get(0).id());
    Workflow old = rows.isEmpty() ? null : rows.get(0);
    String normalized = BankingLanguage.normalize(text);
    if (command != null && (old == null || !old.id().equals(command.actionId())))
      throw new InvalidRequestException(
          "These details have changed. Confirm the latest payment below.");
    boolean pending = old != null && Set.of("COLLECTING", "REVIEW").contains(old.status());
    if (pending) {
      if (OffsetDateTime.now().isAfter(old.expiresAt())) {
        closeProviderReview(old);
        save(
            conversation,
            copy(
                old,
                "EXPIRED",
                null,
                "The confirmation time has passed. Please review the payment again.",
                List.of(),
                null));
        if (command != null || BankingLanguage.continuation(text))
          return copy(
              old,
              "EXPIRED",
              null,
              "The confirmation time has passed. Please start the payment again.",
              List.of(),
              null);
        old = null;
      } else {
        if (command != null && "CANCEL".equals(command.type()) || BankingLanguage.cancel(text)) {
          closeProviderReview(old);
          return save(
              conversation,
              copy(
                  old,
                  "CANCELLED",
                  null,
                  "Okay, I've cancelled this " + noun(old) + ".",
                  List.of(),
                  null));
        }
        if (command != null && "CONFIRM".equals(command.type())) {
          if (!old.status().equals("REVIEW") || !old.executionAvailable())
            throw new InvalidRequestException("Please complete the payment details first.");
          String reference =
              old.operation().equals("OWN_TRANSFER")
                  ? transfers.execute(old.accountId(), old.targetId(), new BigDecimal(old.amount()))
                  : showcase.confirm(old.reference()).reference();
          String outcome =
              old.operation().equals("OWN_TRANSFER")
                  ? "Transferred "
                      + old.currency()
                      + " "
                      + old.amount()
                      + " to "
                      + name(old.targetLabel())
                      + "."
                  : old.operation().equals("CANCEL_MANDATE")
                      ? "Your cancellation for " + name(old.targetLabel()) + " has been accepted."
                      : cardControl(old)
                          ? "Your "
                              + noun(old)
                              + " for "
                              + name(old.targetLabel())
                              + " has been accepted."
                          : "Your payment of "
                              + old.currency()
                              + " "
                              + old.amount()
                              + " to "
                              + name(old.targetLabel())
                              + " has been accepted.";
          return save(conversation, copy(old, "COMPLETED", null, outcome, List.of(), reference));
        }
        if (command != null) {
          if (!command.type().equals("SELECT") || old.status().equals("REVIEW"))
            throw new InvalidRequestException(
                "Choose the payment details below before confirming.");
          return select(conversation, old, command.value());
        }
        String operation = BankingLanguage.operation(text);
        if (operation != null
            && !operation.equals(old.operation())
            && !(old.operation().equals("OWN_TRANSFER")
                && operation.equals("START_TRANSFER")
                && BankingLanguage.continuation(text))) {
          closeProviderReview(old);
          save(conversation, copy(old, "CANCELLED", null, "Let's change that.", List.of(), null));
          return begin(conversation, text, operation);
        }
        // Only self-contained informational questions interrupt. A slot answer never restarts
        // routing.
        if (normalized.matches("show (?:it|that)")) {
          var latestRead =
              db.queryForList(
                  "SELECT banking_content FROM conversation_turns WHERE conversation_id=? ORDER BY"
                      + " sequence_id DESC FETCH NEXT 1 ROWS ONLY",
                  String.class,
                  conversation);
          if (!latestRead.isEmpty() && latestRead.get(0) != null) return null;
        }
        if (BankingLanguage.readRequest(text)
            && !BankingLanguage.continuation(text)
            && !normalized.startsWith("what about ")) return null;
        if (BankingLanguage.guarded(text)
            && !normalized.matches("^(?:no|not|nahi) .+ (?:instead|use) .+$"))
          return copy(
              old,
              old.status(),
              old.field(),
              "Would you like to change this " + noun(old) + " or cancel it?",
              old.choices(),
              old.reference());
        if (BankingLanguage.continuation(text)) {
          if (old.status().equals("REVIEW")) return confirmationPrompt(old);
          if (old.choices().size() == 1)
            return select(conversation, old, old.choices().get(0).id());
          return prompt(old, old.field(), old.choices());
        }
        return fill(conversation, old, text);
      }
    }
    if (command != null) {
      if (old != null && old.status().equals("COMPLETED") && command.type().equals("CONFIRM"))
        return old;
      throw new InvalidRequestException("This payment is closed. Start a new payment to continue.");
    }
    if (old != null && (BankingLanguage.continuation(text) || BankingLanguage.cancel(text))) {
      var latestRead =
          db.queryForList(
              "SELECT banking_content FROM conversation_turns WHERE conversation_id=? ORDER BY"
                  + " sequence_id DESC FETCH NEXT 1 ROWS ONLY",
              String.class,
              conversation);
      if (!latestRead.isEmpty() && latestRead.get(0) != null) return null;
      if (BankingLanguage.cancel(text) && old.status().equals("COMPLETED"))
        return copy(
            old,
            old.status(),
            null,
            "This " + noun(old) + " has already been accepted and cannot be cancelled here.",
            List.of(),
            old.reference());
      return old;
    }
    String operation = BankingLanguage.operation(text);
    if (operation == null
        && !BankingLanguage.guarded(text)
        && !BankingLanguage.readRequest(text)
        && !normalized.matches(".*\\b(how|why|safely|help)\\b.*")) {
      var intent = classifier.classify(text).intent();
      if (Set.of(Intent.START_TRANSFER, Intent.PAY_BILL, Intent.PAY_CARD, Intent.CANCEL_MANDATE)
          .contains(intent)) operation = intent.name();
    }
    return operation == null ? null : begin(conversation, text, operation);
  }

  public Workflow startRequested(String conversation, String text, String operation) {
    return begin(conversation, text, operation);
  }

  private Workflow begin(String conversation, String text, String operation) {
    Workflow w =
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
            null,
            null,
            "",
            List.of(),
            OffsetDateTime.now().plusMinutes(10),
            true,
            true,
            null);
    w = prompt(w, "target", targets(w));
    save(conversation, w);
    if (w.status().equals("UNAVAILABLE")) return w;
    return fill(conversation, w, text);
  }

  private boolean cardControl(Workflow w) {
    return Set.of("FREEZE_CARD", "UNFREEZE_CARD", "REPLACE_CARD").contains(w.operation());
  }

  private String noun(Workflow w) {
    return switch (w.operation()) {
      case "OWN_TRANSFER", "START_TRANSFER" -> "transfer";
      case "CANCEL_MANDATE" -> "direct debit cancellation";
      case "FREEZE_CARD" -> "card freeze";
      case "UNFREEZE_CARD" -> "card unfreeze";
      case "REPLACE_CARD" -> "card replacement";
      default -> "payment";
    };
  }

  private String name(String label) {
    return label == null ? "the recipient" : label.split(" · ")[0];
  }

  private void closeProviderReview(Workflow w) {
    if (w.reference() != null && !w.operation().equals("OWN_TRANSFER"))
      showcase.cancel(w.reference());
  }

  private Workflow confirmationPrompt(Workflow w) {
    return copy(
        w,
        "REVIEW",
        null,
        "Please check the details and use " + confirmLabel(w) + " below.",
        List.of(),
        w.reference());
  }

  private String confirmLabel(Workflow w) {
    return cardControl(w)
        ? "Confirm card change"
        : w.operation().equals("CANCEL_MANDATE")
            ? "Confirm cancellation"
            : w.operation().equals("OWN_TRANSFER") || w.operation().equals("START_TRANSFER")
                ? "Confirm transfer"
                : "Confirm payment";
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
      case "PAY_CARD", "FREEZE_CARD", "UNFREEZE_CARD", "REPLACE_CARD" ->
          cards.list(null, 0, 100).stream()
              .filter(
                  c ->
                      !"CLOSED".equals(c.status())
                          && (!w.operation().equals("PAY_CARD")
                              || "CREDIT".equals(c.cardType())
                                  && "ACTIVE".equals(c.status())
                                  && c.outstanding() != null
                                  && new BigDecimal(c.outstanding()).signum() > 0))
              .map(c -> new Workflow.Choice(c.id(), c.displayName() + " · " + c.numberMasked()))
              .toList();
      case "CANCEL_MANDATE" ->
          mandates.list(null, 0, 100).stream()
              .filter(m -> Set.of("ACTIVE", "PAUSED", "ACTION_REQUIRED").contains(m.status()))
              .map(m -> new Workflow.Choice(m.id(), m.payee()))
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

  private List<Workflow.Choice> sources(Workflow w) {
    return accounts.currentAccounts().stream()
        .filter(
            a ->
                "ACTIVE".equals(a.status())
                    && (!w.operation().equals("OWN_TRANSFER") || !a.id().equals(w.targetId())))
        .map(a -> new Workflow.Choice(a.id(), a.displayName() + " · " + a.accountNumberMasked()))
        .toList();
  }

  private List<Workflow.Choice> matches(List<Workflow.Choice> choices, String text) {
    if (text == null || text.isBlank()) return List.of();
    String normalized = BankingLanguage.normalize(text);
    var exact =
        choices.stream()
            .filter(
                c ->
                    c.id().equals(text)
                        || (!c.id().matches("[0-9]+")
                            && Arrays.asList(normalized.split("\\s+"))
                                .contains(c.id().toLowerCase(Locale.ROOT)))
                        || BankingLanguage.normalize(name(c.label())).equals(normalized))
            .toList();
    if (!exact.isEmpty()) return exact;
    var tokens = new HashSet<>(Arrays.asList(normalized.split("[^\\p{L}\\p{N}_-]+")));
    tokens.removeAll(
        Set.of(
            "pay",
            "bill",
            "the",
            "my",
            "account",
            "accounts",
            "from",
            "to",
            "se",
            "ko",
            "karo",
            "do",
            "kar",
            "please",
            "instead",
            "actually",
            "use",
            "change",
            "it",
            "card",
            "credit",
            "debit",
            "money",
            "send",
            "transfer",
            "demo",
            "bank"));
    int best = 0;
    var found = new ArrayList<Workflow.Choice>();
    for (var choice : choices) {
      var words =
          new HashSet<>(
              Arrays.asList(
                  BankingLanguage.normalize(name(choice.label())).split("[^\\p{L}\\p{N}_-]+")));
      words.retainAll(tokens);
      int score = words.size();
      if (score > best) {
        found.clear();
        best = score;
      }
      if (score > 0 && score == best) found.add(choice);
    }
    return found;
  }

  private Workflow details(
      Workflow w,
      String account,
      String target,
      String accountLabel,
      String targetLabel,
      String amount) {
    return new Workflow(
        1,
        w.id(),
        w.operation(),
        "COLLECTING",
        w.field(),
        account,
        target,
        accountLabel,
        targetLabel,
        amount,
        w.currency(),
        w.message(),
        w.choices(),
        w.expiresAt(),
        true,
        true,
        null);
  }

  private Workflow revised(String conversation, Workflow w) {
    if (!w.status().equals("REVIEW")) return w;
    closeProviderReview(w);
    save(
        conversation,
        copy(w, "CANCELLED", null, "The payment details have changed.", List.of(), null));
    return new Workflow(
        1,
        UUID.randomUUID().toString(),
        w.operation(),
        "COLLECTING",
        w.field(),
        w.accountId(),
        w.targetId(),
        w.accountLabel(),
        w.targetLabel(),
        w.amount(),
        w.currency(),
        "",
        List.of(),
        OffsetDateTime.now().plusMinutes(10),
        true,
        true,
        null);
  }

  private Workflow select(String conversation, Workflow w, String value) {
    var selected = w.choices().stream().filter(c -> c.id().equals(value)).findFirst();
    if (selected.isEmpty())
      throw new InvalidRequestException("Please choose one of the options below.");
    var c = selected.get();
    Workflow next =
        w.field().equals("target")
            ? details(w, w.accountId(), c.id(), w.accountLabel(), c.label(), w.amount())
            : details(w, c.id(), w.targetId(), c.label(), w.targetLabel(), w.amount());
    return progress(conversation, next);
  }

  private Workflow fill(String conversation, Workflow w, String input) {
    String text = BankingLanguage.normalize(input);
    String sourceText = null, targetText = text;
    boolean sourceBeforeSe = false;
    var from =
        java.util.regex.Pattern.compile("(?:\\bfrom|\\buse) (.+?)(?: to | for |$)").matcher(text);
    var se = java.util.regex.Pattern.compile("(.+?) se(?: |$)").matcher(text);
    if (from.find()) {
      sourceText = from.group(1);
      targetText = text.replace(from.group(), " ");
    } else if (se.find()) {
      sourceText = se.group(1);
      sourceBeforeSe = true;
    }
    if (text.matches(".*\\b(change|different|another)\\b.*\\b(account|source)\\b.*")
        && sourceText == null) {
      var next = revised(conversation, w);
      return save(
          conversation,
          prompt(
              details(next, null, next.targetId(), null, next.targetLabel(), next.amount()),
              "account",
              sources(next)));
    }
    if (text.matches(
            ".*\\b(change|different|another)\\b.*\\b(bill|recipient|payee|beneficiary|destination)\\b.*")
        && matches(targets(w), targetText).isEmpty()) {
      var next = revised(conversation, w);
      return save(
          conversation,
          prompt(
              details(
                  next,
                  next.accountId(),
                  null,
                  next.accountLabel(),
                  null,
                  next.operation().equals("PAY_BILL") ? null : next.amount()),
              "target",
              targets(next)));
    }
    var targetMatches =
        "amount".equals(w.field()) && text.matches("[0-9.,]+")
            ? List.<Workflow.Choice>of()
            : matches(targets(w), targetText);
    var sourceMatches =
        matches(sources(w), sourceText == null && "account".equals(w.field()) ? text : sourceText);
    if (sourceBeforeSe && sourceMatches.size() == 1)
      targetMatches =
          matches(
              targets(w),
              text.replace(BankingLanguage.normalize(name(sourceMatches.get(0).label())), ""));
    if (sourceMatches.size() == 1
        && "account".equals(w.field())
        && sourceText == null
        && !text.matches(".*\\b(to|ko)\\b.*")) targetMatches = List.of();
    String account = w.accountId(),
        target = w.targetId(),
        accountLabel = w.accountLabel(),
        targetLabel = w.targetLabel(),
        amount = w.amount();
    boolean changed = false;
    if (targetMatches.size() == 1 && !targetMatches.get(0).id().equals(target)) {
      target = targetMatches.get(0).id();
      targetLabel = targetMatches.get(0).label();
      changed = true;
      if (w.operation().equals("PAY_BILL") || w.operation().equals("PAY_CARD")) amount = null;
      if (w.operation().equals("CANCEL_MANDATE") || cardControl(w)) {
        account = null;
        accountLabel = null;
      }
    }
    if (sourceMatches.size() == 1 && !sourceMatches.get(0).id().equals(account)) {
      account = sourceMatches.get(0).id();
      accountLabel = sourceMatches.get(0).label();
      changed = true;
    }
    // IDs and account suffixes must not be interpreted as amounts.
    String amountText = null;
    var amountMatch =
        java.util.regex.Pattern.compile(
                "(?:^|\\b(?:amount|pay|send|transfer|make"
                    + " it|instead|inr|rs)\\s+|₹)([+-]?[0-9][0-9,]*(?:\\.[0-9]+)?)(?:$|\\s)")
            .matcher(text);
    if (amountMatch.find()
        && !text.matches(".*\\b(account|id)\\s+[0-9]+.*")
        && (targetMatches.isEmpty() && sourceMatches.isEmpty()
            || !text.matches("[0-9.,]+") && !targetMatches.isEmpty()))
      amountText = amountMatch.group(1);
    // Explicit currency or amount verbs remain unambiguous even alongside named accounts/payees.
    var explicit =
        java.util.regex.Pattern.compile(
                "(?:\\b(?:amount|pay|send|transfer|make"
                    + " it|inr|rs)\\s+|₹)([+-]?[0-9][0-9,]*(?:\\.[0-9]+)?)(?![\\p{L}\\p{N}.,])|([+-]?[0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(?:inr|rupees?)\\b")
            .matcher(text);
    if (explicit.find())
      amountText = explicit.group(1) == null ? explicit.group(2) : explicit.group(1);
    if (amountText == null) {
      var beforeVerb =
          java.util.regex.Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+(?:send|pay)\\b")
              .matcher(text);
      if (beforeVerb.find()) amountText = beforeVerb.group(1);
    }
    if (amountText != null && !cardControl(w) && !w.operation().equals("CANCEL_MANDATE")) {
      if (!amountText.matches("(?:[0-9]{1,13}|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?")
          || new BigDecimal(amountText.replace(",", "")).signum() <= 0)
        return save(
            conversation,
            copy(
                details(revised(conversation, w), account, target, accountLabel, targetLabel, null),
                "COLLECTING",
                "amount",
                "Enter an amount greater than zero with up to two decimal places.",
                List.of(),
                null));
      String decimal = amountText.replace(",", "");
      if (!decimal.equals(amount)) {
        amount = decimal;
        changed = true;
      }
    }
    if (amountText == null && text.matches("^(?:make it|amount|instead) .+")) {
      var next =
          details(revised(conversation, w), account, target, accountLabel, targetLabel, null);
      return save(conversation, prompt(next, "amount", List.of()));
    }
    String targetHint =
        targetText
            .replaceAll(
                "\\b(pay|payment|bill|send|transfer|money|my|the|please|do|it|karo|kar|to|ko|actually|instead|from|inr|rs)\\b|[0-9.,₹]",
                " ")
            .trim();
    if (targetMatches.isEmpty()
        && w.operation().equals(BankingLanguage.operation(input))
        && !targetHint.isEmpty()
        && sourceMatches.isEmpty()
        && w.targetId() != null) {
      var next =
          details(
              revised(conversation, w),
              account,
              null,
              accountLabel,
              null,
              w.operation().equals("PAY_BILL") ? null : amount);
      return save(conversation, prompt(next, "target", targets(next)));
    }
    if (targetMatches.size() > 1) {
      var next =
          details(
              revised(conversation, w),
              account,
              null,
              accountLabel,
              null,
              w.operation().equals("PAY_BILL") ? null : amount);
      return save(
          conversation,
          copy(
              next,
              "COLLECTING",
              "target",
              "I found more than one match. Which one do you mean?",
              targetMatches,
              null));
    }
    if (sourceMatches.size() > 1 || sourceText != null && sourceMatches.isEmpty()) {
      var next = details(revised(conversation, w), null, target, null, targetLabel, amount);
      return save(
          conversation,
          prompt(next, "account", sourceMatches.isEmpty() ? sources(next) : sourceMatches));
    }
    if (!changed && w.status().equals("REVIEW")) return confirmationPrompt(w);
    var next =
        changed
            ? details(revised(conversation, w), account, target, accountLabel, targetLabel, amount)
            : w;
    return progress(conversation, next);
  }

  private Workflow progress(String conversation, Workflow w) {
    if (w.targetId() == null) return save(conversation, prompt(w, "target", targets(w)));
    String account = w.accountId(), accountLabel = w.accountLabel(), amount = w.amount();
    if (w.operation().equals("CANCEL_MANDATE") && account == null)
      account = mandates.detail(w.targetId()).accountId();
    if (cardControl(w) && account == null) account = cards.detail(w.targetId()).accountId();
    if (amount == null && !"amount".equals(w.field()) && w.operation().equals("PAY_BILL"))
      amount = bills.detail(w.targetId()).amount();
    if (amount == null && !"amount".equals(w.field()) && w.operation().equals("PAY_CARD"))
      amount = cards.detail(w.targetId()).outstanding();
    if (account != null && accountLabel == null) {
      var owned = accounts.requireOwnedAccount(account);
      accountLabel = owned.displayName() + " · " + owned.accountNumberMasked();
    }
    var next = details(w, account, w.targetId(), accountLabel, w.targetLabel(), amount);
    if (account == null) return save(conversation, prompt(next, "account", sources(next)));
    if (amount == null && !cardControl(w) && !w.operation().equals("CANCEL_MANDATE"))
      return save(conversation, prompt(next, "amount", List.of()));
    String reference = null;
    try {
      if (w.operation().equals("OWN_TRANSFER"))
        transfers.validate(account, w.targetId(), new BigDecimal(amount));
      else
        reference =
            showcase
                .prepare(
                    w.operation(),
                    account,
                    w.targetId(),
                    amount == null ? null : new BigDecimal(amount))
                .id();
    } catch (InvalidRequestException | ResourceNotFoundException ex) {
      String field =
          ex instanceof ResourceNotFoundException
              ? "target"
              : ex.getMessage()
                      .toLowerCase(Locale.ROOT)
                      .matches(".*(account|currency|currencies).*")
                  ? "account"
                  : "amount";
      if (field.equals("target"))
        return save(
            conversation,
            prompt(
                details(next, next.accountId(), null, next.accountLabel(), null, next.amount()),
                "target",
                targets(next)));
      return save(
          conversation,
          copy(
              next,
              "COLLECTING",
              field,
              ex.getMessage()
                  + (field.equals("account")
                      ? " Choose another account."
                      : " Enter another amount, or choose a different account."),
              field.equals("account") ? sources(next) : List.of(),
              null));
    }
    String summary =
        cardControl(next)
            ? "Confirm the " + noun(next) + " for " + name(next.targetLabel()) + "."
            : next.operation().equals("CANCEL_MANDATE")
                ? "Cancel the direct debit to " + name(next.targetLabel()) + "?"
                : "Pay "
                    + accounts.requireOwnedAccount(account).currencyCode()
                    + " "
                    + amount
                    + " to "
                    + name(next.targetLabel())
                    + " from "
                    + name(accountLabel)
                    + "?";
    return save(
        conversation,
        new Workflow(
            1,
            next.id(),
            next.operation(),
            "REVIEW",
            null,
            account,
            next.targetId(),
            accountLabel,
            next.targetLabel(),
            amount,
            accounts.requireOwnedAccount(account).currencyCode(),
            summary,
            List.of(),
            next.expiresAt(),
            true,
            true,
            reference));
  }

  private Workflow prompt(Workflow w, String field, List<Workflow.Choice> choices) {
    String message =
        switch (field) {
          case "target" ->
              w.operation().equals("OWN_TRANSFER")
                  ? "Which account should receive the money?"
                  : w.operation().equals("PAY_BILL")
                      ? "Which bill would you like to pay?"
                      : w.operation().equals("CANCEL_MANDATE")
                          ? "Which direct debit would you like to cancel?"
                          : cardControl(w) || w.operation().equals("PAY_CARD")
                              ? "Which card would you like to use?"
                              : "Who would you like to pay?";
          case "account" ->
              (w.targetLabel() == null ? "" : "For " + name(w.targetLabel()) + ", ")
                  + "which account should the money come from?";
          default ->
              "How much would you like to "
                  + (w.operation().equals("PAY_BILL") || w.operation().equals("PAY_CARD")
                      ? "pay"
                      : "transfer")
                  + "?";
        };
    if (!field.equals("amount") && choices.isEmpty())
      return copy(
          w,
          "UNAVAILABLE",
          null,
          field.equals("account")
              ? "You don't have an eligible account for this payment."
              : "There are no eligible "
                  + (w.operation().equals("PAY_BILL")
                      ? "unpaid bills"
                      : cardControl(w) || w.operation().equals("PAY_CARD")
                          ? "cards"
                          : w.operation().equals("CANCEL_MANDATE") ? "direct debits" : "recipients")
                  + " to choose from.",
          List.of(),
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
