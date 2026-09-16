package com.nexa.api.conversations;

import com.nexa.api.nlp.*;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConversationInterpreter {
  public record Interpretation(
      String intent, String essence, String reply, BankingContent banking) {
    public Interpretation(String intent, String essence, String reply) {
      this(intent, essence, reply, null);
    }
  }

  private final IntentClassifier classifier;
  private final EntityExtractor extractor;
  private final DomainRouter router;
  private final boolean debug;
  private ConversationInsights insights;
  private OllamaInterpreter ollama;

  @org.springframework.beans.factory.annotation.Autowired
  public void setOllama(OllamaInterpreter ollama) {
    this.ollama = ollama;
  }

  public boolean usesLocalModel() {
    return ollama != null && ollama.enabled();
  }

  public boolean isFastRequest(String text) {
    return FastBankingIntent.match(text) != null;
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ConversationInterpreter(
      IntentClassifier classifier,
      EntityExtractor extractor,
      DomainRouter router,
      @Value("${NLP_DEBUG_METADATA:false}") boolean debug,
      ConversationInsights insights) {
    this(classifier, extractor, router, debug);
    this.insights = insights;
  }

  public ConversationInterpreter(
      IntentClassifier classifier,
      EntityExtractor extractor,
      DomainRouter router,
      @Value("${NLP_DEBUG_METADATA:false}") boolean debug) {
    this.classifier = classifier;
    this.extractor = extractor;
    this.router = router;
    this.debug = debug;
  }

  public Interpretation interpret(String text) {
    return interpret(text, java.util.List.of());
  }

  public Interpretation interpret(String text, java.util.List<OllamaInterpreter.Message> history) {
    var fastIntent = FastBankingIntent.match(text);
    if (fastIntent != null)
      return routed(new IntentClassifier.Match(fastIntent, 1), extractor.extract(text, fastIntent));
    // Spending always reaches the same structured parser, whether a local model is enabled or not.
    if (insights != null && text.toLowerCase(java.util.Locale.ROOT).matches(".*\\b(spend|spent|spending)\\b.*")) {
      var result = insights.interpret(text);
      if (result != null) return result;
    }
    var conversational = BankingLanguage.intent(text);
    if (conversational != null
        && !text.toLowerCase().matches(".*\\b(yesterday|above|below|more than|less than)\\b.*")
        && (text.matches(".*[\\u0900-\\u097f].*")
            || text.matches("(?i).*\\b(karo|batao|bhejo|dikhao)\\b.*")))
      return routed(
          new IntentClassifier.Match(conversational, 1), extractor.extract(text, conversational));
    var plan = ollama == null ? null : ollama.interpret(text, history);
    if (plan != null) {
      if (java.util.Set.of("START_TRANSFER", "PAY_BILL", "PAY_CARD", "CANCEL_MANDATE")
          .contains(plan.intent()))
        return new Interpretation(
            plan.intent(),
            "Banking action requested.",
            plan.intent().equals("START_TRANSFER")
                ? "Who would you like to pay?"
                : plan.intent().equals("PAY_BILL")
                    ? "Which bill would you like to pay?"
                    : plan.intent().equals("PAY_CARD")
                        ? "Which card would you like to pay?"
                        : "Which direct debit would you like to cancel?");
      if (java.util.Set.of("GET_SPENDING", "GET_SALARY", "DECLINE_HELP").contains(plan.intent())
          && insights != null) {
        if (!plan.intent().equals("DECLINE_HELP") && plan.period() == null)
          return new Interpretation(
              plan.intent(), "Choose a period.", "Would you like this month or last month?");
        String period = "LAST_MONTH".equals(plan.period()) ? "last month" : "this month";
        return insights.interpret(
            switch (plan.intent()) {
              case "GET_SPENDING" ->
                  "spending " + period + ("food".equalsIgnoreCase(plan.search()) ? " food" : "");
              case "GET_SALARY" -> "salary " + period;
              default -> "declined";
            });
      }
      var intent = Intent.valueOf(plan.intent());
      return routed(new IntentClassifier.Match(intent, 1), ollama.entities(plan));
    }
    if (insights != null) {
      var result = insights.interpret(text);
      if (result != null) return result;
    }
    var match = classifier.classify(text);
    var entities = extractor.extract(text, match.intent());
    return routed(match, entities);
  }

  /** Resolve read follow-ups from owned structured context, then query current banking data. */
  public Interpretation followUp(
      String text, String previousIntent, BankingContent content, String previousText) {
    String normalized = BankingLanguage.normalize(text);
    if (insights != null
        && java.util.Set.of("GET_SPENDING", "GET_SALARY").contains(previousIntent)
        && normalized.matches("(?:this|last) month"))
      return insights.interpret(
          (previousIntent.equals("GET_SALARY") ? "salary " : "spending ")
              + normalized
              + (previousText.toLowerCase().contains("food") ? " food" : ""));
    if (content == null || BankingLanguage.guarded(text)) return null;
    Intent intent;
    try {
      intent = Intent.valueOf(previousIntent);
    } catch (IllegalArgumentException ex) {
      return null;
    }
    if (!intent.name().startsWith("GET_")) return null;
    var extracted = extractor.extract(text, intent);
    var previous = extractor.extract(previousText, intent);
    if (content.meta() != null && content.meta().get("query") instanceof Map<?, ?> query)
      previous =
          new EntityExtractor.Entities(
              null,
              string(query, "account"),
              string(query, "target"),
              null,
              date(query, "from"),
              date(query, "to"),
              string(query, "accountType"),
              string(query, "direction"),
              string(query, "search"),
              string(query, "status"));
    String account = extracted.accountId() == null ? previous.accountId() : extracted.accountId(),
        target = extracted.targetId() == null ? previous.targetId() : extracted.targetId();
    if (content.account() != null) account = content.account().id();
    if (content.accounts() != null) {
      var matches =
          content.accounts().stream()
              .filter(a -> normalized.contains(BankingLanguage.normalize(a.displayName())))
              .toList();
      if (matches.size() == 1) account = matches.get(0).id();
      else if (content.accounts().size() == 1) account = content.accounts().get(0).id();
    }
    if (content.bills() != null) {
      var matches =
          content.bills().stream()
              .filter(
                  b ->
                      b.billerName() != null
                          && normalized.contains(BankingLanguage.normalize(b.billerName())))
              .toList();
      if (matches.size() == 1) target = matches.get(0).id();
      else if (content.bills().size() == 1) target = content.bills().get(0).id();
      if (target != null) intent = Intent.GET_BILL_DETAIL;
    }
    if (content.transactions() != null
        && normalized.matches(".*\\b(first|latest|last one|details)\\b.*")
        && !content.transactions().isEmpty()) {
      if (content.transactions().size() == 1 || normalized.matches(".*\\b(first|latest)\\b.*")) {
        target = content.transactions().get(0).id();
        intent = Intent.GET_TRANSACTION_DETAIL;
      } else
        return new Interpretation(
            "GET_RECENT_TRANSACTIONS",
            "Choose a transaction.",
            "Which transaction would you like to see?",
            content);
    }
    return routed(
        new IntentClassifier.Match(intent, 1),
        new EntityExtractor.Entities(
            null,
            account,
            target,
            null,
            extracted.from() == null ? previous.from() : extracted.from(),
            extracted.to() == null ? previous.to() : extracted.to(),
            extracted.accountType() == null ? previous.accountType() : extracted.accountType(),
            extracted.direction() == null ? previous.direction() : extracted.direction(),
            extracted.search() == null ? previous.search() : extracted.search(),
            extracted.status() == null ? previous.status() : extracted.status()));
  }

  private static String string(Map<?, ?> values, String key) {
    return values.get(key) == null ? null : values.get(key).toString();
  }

  public Interpretation readForAccount(String text, String accountId) {
    Intent intent = FastBankingIntent.match(text);
    var e = extractor.extract(text, intent);
    return routed(
        new IntentClassifier.Match(intent, 1),
        new EntityExtractor.Entities(
            null,
            accountId,
            e.targetId(),
            null,
            e.from(),
            e.to(),
            e.accountType(),
            e.direction(),
            e.search(),
            e.status()));
  }

  private static java.time.LocalDate date(Map<?, ?> values, String key) {
    return values.get(key) == null ? null : java.time.LocalDate.parse(values.get(key).toString());
  }

  private Interpretation routed(IntentClassifier.Match match, EntityExtractor.Entities entities) {
    var reply = router.route(match.intent(), entities);
    String essence =
        match.intent() == Intent.UNKNOWN
            ? "Unclear request; clarification needed."
            : match.intent().name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT) + ".";
    if (match.intent() == Intent.START_TRANSFER
        && entities.amount() != null
        && entities.payee() != null)
      essence =
          "Request to transfer INR "
              + entities.amount().toPlainString()
              + " to "
              + entities.payee().trim().toLowerCase(java.util.Locale.ROOT)
              + ".";
    if (entities.from() != null || entities.to() != null)
      essence += " Date range " + entities.from() + " to " + entities.to() + ".";
    if (entities.accountType() != null) essence += " " + entities.accountType() + " account.";
    if (entities.targetId() != null) essence += " Reference " + entities.targetId() + ".";
    BankingContent data =
        reply.data() == null && (!reply.type().equals("TEXT") || reply.errorCode() != null)
            ? new BankingContent(1, reply.type(), null, null, null, 0)
            : reply.data();
    if (data != null) {
      var meta = new java.util.HashMap<String, Object>();
      var query = new java.util.HashMap<String, Object>();
      if (entities.accountId() != null) query.put("account", entities.accountId());
      if (entities.targetId() != null) query.put("target", entities.targetId());
      if (entities.from() != null) query.put("from", entities.from().toString());
      if (entities.to() != null) query.put("to", entities.to().toString());
      if (entities.accountType() != null) query.put("accountType", entities.accountType());
      if (entities.direction() != null) query.put("direction", entities.direction());
      if (entities.search() != null) query.put("search", entities.search());
      if (entities.status() != null) query.put("status", entities.status());
      if (!query.isEmpty()) meta.put("query", query);
      if (debug) {
        meta.put("intent", match.intent().name());
        meta.put("confidence", match.confidence());
      }
      data = data.describe(reply.type(), reply.errorCode(), meta.isEmpty() ? null : meta);
    }
    if (debug)
      LoggerFactory.getLogger(getClass())
          .debug(
              "Banking route intent={} confidence={} response={}",
              match.intent(),
              match.confidence(),
              reply.type());
    return new Interpretation(match.intent().name(), essence, reply.message(), data);
  }
}
