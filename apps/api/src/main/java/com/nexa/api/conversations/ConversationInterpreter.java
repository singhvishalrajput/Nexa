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
    var match = classifier.classify(text);
    var entities = extractor.extract(text, match.intent());
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
    if (data != null)
      data =
          data.describe(
              reply.type(),
              reply.errorCode(),
              debug
                  ? Map.of("intent", match.intent().name(), "confidence", match.confidence())
                  : null);
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
