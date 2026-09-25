package com.nexa.api.service;

import com.nexa.api.beans.BankingContent;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** A read-only boundary before workflow interpretation. Unknown terms fail closed. */
@Service
public class KnowledgeRouter {
  public enum Category {
    KNOWLEDGE,
    NEXA_KNOWLEDGE,
    ACCOUNT_DATA,
    TRANSACTION,
    HELP,
    ADMIN,
    UNKNOWN
  }

  public record Decision(Category category, ConversationInterpreter.Interpretation answer) {}

  private final KnowledgeBase kb;
  private final LoanCalculationService loans;

  public KnowledgeRouter(KnowledgeBase kb, LoanCalculationService loans) {
    this.kb = kb;
    this.loans = loans;
  }

  private static boolean has(String text, String expression) {
    return java.util.regex.Pattern.compile("(?iu)(?:" + expression + ")").matcher(text).find();
  }

  public Decision route(String input, String previousTopic) {
    String text = KnowledgeBase.normalize(input);
    String language =
        has(text, "[\\u0900-\\u097f]")
            ? "hi"
            : has(text, "\\b(kya|kaise|hota|hoti|hai|hain|kitna|kitne|chahiye|mera|meri)\\b")
                ? "hinglish"
                : "en";
    var auth = SecurityContextHolder.getContext().getAuthentication();
    boolean admin =
        auth != null
            && auth.isAuthenticated()
            && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    boolean help =
        has(
            text,
            "^(how (do|can) i|where (can|do)|where is|help me understand)|\\bkaise\\b|कैसे|कहाँ");
    boolean definition =
        has(
            text,
            "^(what (is|are|does)|explain|define|tell me about|how (does|do|is))\\b|kya"
                + " (hota|hoti|hai)|क्या.*(है|होता)|समझा");
    boolean info =
        help
            || definition
            || has(
                text,
                "^(can i|can nexa|does nexa|what can|which|what documents|what do i need|what"
                    + " loans|what happens|what types|what rates|what fees|what limits|is there|do"
                    + " i need|why|when)\\b");
    if (has(
        text,
        "\\b(admin|administrator|audit|ledger|journal|approve|reject)\\b|(?:block|reactivate|close|rename).*customer|controlled"
            + " (deposit|withdrawal)")) {
      if (!admin)
        return answer(
            Category.ADMIN,
            "",
            language,
            "Administrator capabilities require an authorized staff session. They are unavailable"
                + " in customer chat.",
            null,
            "FORBIDDEN");
      if (!info)
        return answer(
            Category.ADMIN,
            "admin",
            language,
            "Use the administrator workspace to review and confirm this operation. No"
                + " administrative action was executed in chat.",
            null,
            null);
      return retrieve(Category.ADMIN, "admin", "OVERVIEW", true, true, language);
    }
    // Personal read requests win over broad question words, but instructional 'how do I' does not.
    boolean personal =
        !help
            && has(
                text, "\\b(my|mine|i have|did i|did my|do i have|mera|meri|mere)\\b|मेरे|मेरा|मेरी")
            && !has(text, "^(can i|what documents|what do i need|how do|how can)|calculate my emi");
    if (info && has(text, "\\b(and|then)\\s+(transfer|send|pay|freeze|open|create)\\b"))
      return answer(
          Category.TRANSACTION,
          "",
          language,
          "Please ask the information question and the banking action separately so the action can"
              + " be reviewed before confirmation.",
          null,
          "SEPARATE_REQUESTS_REQUIRED");
    boolean action =
        !info
            && has(
                text,
                "\\b(transfer|send|pay|repay|freeze|unfreeze|replace|create|schedule|open|add|cancel|prepay|payoff|withdraw|deposit)\\b|make.*(?:payment|prepayment)|bhejo|karo|kar"
                    + " do|भेज|करो|कर दो");
    if (action) {
      if (has(text, "\\b(schedule|scheduled)\\b") && !has(text, "\\bloan\\b"))
        return answer(
            Category.TRANSACTION,
            "",
            language,
            "Scheduled-payment creation is not currently available in this chat. The Scheduled"
                + " payments screen shows recorded schedules. No payment was created or executed.",
            null,
            "UNSUPPORTED_ACTION");
      // Existing chat workflows handle supported commands; other actions must not fall into NLP
      // reads.
      if (has(text, "\\b(schedule|scheduled|prepay|prepayment|payoff|pay off|open|add)\\b"))
        return answer(
            Category.TRANSACTION,
            "",
            language,
            "Open the relevant banking screen to enter the details and review the request before"
                + " confirming. Nothing has been executed by this message.",
            null,
            "SCREEN_WORKFLOW_REQUIRED");
      return new Decision(Category.TRANSACTION, null);
    }
    if (personal || has(text, "^(show|list|check|view)\\b|^(did.*salary)|dikhao|दिखा"))
      return new Decision(Category.ACCOUNT_DATA, null);

    if (has(text, "\\b(calculate|quote)\\b") && has(text, "\\b(emi|loan)\\b") && has(text, "\\d"))
      return calculateQuote(text, language);

    Set<String> topics = kb.topics(text, admin);
    String aspect = "OVERVIEW";
    if (!has(
        text, "^what is (an? )?(interest rate|loan tenure|loan principal)|kya hota|क्या होता")) {
      if (has(text, "\\b(rate|rates|interest|apr)\\b|ब्याज|byaj")) aspect = "RATE";
      if (has(text, "\\b(fee|fees|charge|charges|penalty)\\b|शुल्क")) aspect = "FEES";
      if (has(text, "\\b(limit|limits|minimum|maximum|tenure options)\\b")) aspect = "LIMITS";
      if (has(text, "\\b(eligible|eligibility|qualify|requirements|requirement|terms|rules)\\b"))
        aspect = "REQUIREMENTS";
      if (has(text, "\\b(document|documents|salary slip|salary slips)\\b|दस्तावेज"))
        aspect = "DOCUMENTS";
    }
    boolean followup =
        !aspect.equals("OVERVIEW")
            && (topics.isEmpty() || topics.equals(Set.of("interest")))
            && previousTopic != null;
    if (followup) topics = Set.of(previousTopic);
    boolean terms = !aspect.equals("OVERVIEW");
    if (!info && !terms && !has(text, "kya|क्या")) return new Decision(Category.UNKNOWN, null);
    if (topics.size() != 1)
      return answer(
          Category.KNOWLEDGE,
          "",
          language,
          "Which banking product or feature do you mean? I do not have enough verified information"
              + " to answer that yet.",
          null,
          "KNOWLEDGE_UNAVAILABLE");
    String topic = topics.iterator().next();
    if (topic.equals("nexa")
        && !has(text, "^what (can|is)|features|capabilities|what does nexa do"))
      return unavailable(Category.NEXA_KNOWLEDGE, topic, language);
    boolean nexa =
        terms
            || help
            || has(text, "\\bnexa\\b|^(can i|which currencies|what documents|does|can|where)\\b");
    Category category = help ? Category.HELP : nexa ? Category.NEXA_KNOWLEDGE : Category.KNOWLEDGE;
    // Explicit requests for unsupported product variants must never inherit generic loan terms.
    if (terms && has(text, "\\b(home|car|business|education|mortgage)\\b"))
      return unavailable(category, topic, language);
    return retrieve(category, topic, aspect, nexa, admin, language);
  }

  private Decision retrieve(
      Category category,
      String topic,
      String aspect,
      boolean nexa,
      boolean admin,
      String language) {
    var entry = kb.find(topic, aspect, nexa, admin);
    if (entry.isPresent())
      return answer(category, topic, language, entry.get().localized(language), entry.get(), null);
    // Configuration is second priority, never guessed or independently hard-coded.
    if (Set.of("loan", "personal-loan", "emi").contains(topic)) {
      if (aspect.equals("RATE"))
        return answer(
            category,
            topic,
            language,
            "Nexa's configured loan annual interest rate is "
                + loans.annualInterestRate().toPlainString()
                + "%. Your accepted loan terms come from your authenticated loan record.",
            null,
            null);
      if (aspect.equals("LIMITS"))
        return answer(
            category,
            topic,
            language,
            "Nexa loan quotes support INR "
                + LoanCalculationService.MIN_AMOUNT.toPlainString()
                + " to "
                + LoanCalculationService.MAX_AMOUNT.toPlainString()
                + " and 1 to "
                + LoanCalculationService.MAX_MONTHS
                + " months. A quote is not an approval.",
            null,
            null);
    }
    return unavailable(category, topic, language);
  }

  private Decision calculateQuote(String text, String language) {
    var inputs =
        java.util.regex.Pattern.compile(
                "^(?:please )?(?:calculate (?:my )?emi|loan quote) for"
                    + " (?:inr\\s*|₹\\s*)?([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?) (?:over|for)"
                    + " ([0-9]{1,3}) months?\\.?$")
            .matcher(text);
    if (!inputs.matches())
      return answer(
          Category.NEXA_KNOWLEDGE,
          "emi",
          language,
          "For a Nexa quote, specify the amount in INR and tenure in months, for example: calculate"
              + " EMI for INR 10000 over 12 months. Nexa uses its configured annual rate. You can"
              + " also use the calculator in Loans.",
          null,
          "QUOTE_DETAILS_REQUIRED");
    try {
      var quote =
          loans.quote(
              new com.nexa.api.beans.LoanModels.QuoteRequest(
                  new java.math.BigDecimal(inputs.group(1).replace(",", "")),
                  Integer.valueOf(inputs.group(2))));
      return answer(
          Category.NEXA_KNOWLEDGE,
          "emi",
          language,
          "Nexa loan quote: regular EMI INR "
              + quote.emiAmount().toPlainString()
              + ", final EMI INR "
              + quote.finalEmiAmount().toPlainString()
              + " over "
              + quote.tenureMonths()
              + " months at "
              + quote.annualInterestRate().toPlainString()
              + "% annually. Total scheduled interest is INR "
              + quote.totalInterest().toPlainString()
              + ". This calculation is not an approval or a payment.",
          null,
          null);
    } catch (com.nexa.api.exep.InvalidRequestException | NumberFormatException ex) {
      return answer(
          Category.NEXA_KNOWLEDGE,
          "emi",
          language,
          "These values are outside Nexa's supported quote inputs. Use the calculator in Loans to"
              + " enter a valid amount and tenure.",
          null,
          "INVALID_QUOTE_INPUT");
    }
  }

  private Decision unavailable(Category category, String topic, String language) {
    String message =
        "hi".equals(language)
            ? "इस विषय की सत्यापित जानकारी अभी Nexa Knowledge Base में उपलब्ध नहीं है। मैं अनुमान"
                  + " नहीं लगाऊँगा।"
            : "hinglish".equals(language)
                ? "Is topic ki verified jankari abhi Nexa Knowledge Base mein available nahi hai."
                      + " Main guess nahi karunga."
                : "Verified information for this question is currently unavailable in the Nexa"
                      + " Knowledge Base. I cannot supply an unverified rate, fee, limit or product"
                      + " rule.";
    return answer(category, topic, language, message, null, "KNOWLEDGE_UNAVAILABLE");
  }

  private Decision answer(
      Category category,
      String topic,
      String language,
      String text,
      KnowledgeBase.Entry entry,
      String error) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("category", category.name());
    metadata.put("knowledgeTopic", topic);
    metadata.put("language", language);
    if (entry != null) {
      metadata.put("knowledgeId", entry.id());
      metadata.put("knowledgeVersion", entry.version());
      metadata.put("source", entry.source());
    } else if (error == null && text.startsWith("Nexa"))
      metadata.put("source", "LoanCalculationService product configuration");
    var content =
        new BankingContent(1, "TEXT", null, null, null, 0).describe("TEXT", error, metadata);
    return new Decision(
        category,
        new ConversationInterpreter.Interpretation(
            category.name(), "Knowledge: " + topic, text, content));
  }
}
