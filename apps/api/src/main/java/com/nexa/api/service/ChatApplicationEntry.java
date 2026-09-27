package com.nexa.api.service;

import com.nexa.api.beans.BankingContent;
import com.nexa.api.beans.BankingLanguage;

/** Explicit, display-only entries to authenticated forms; text never submits an application. */
public final class ChatApplicationEntry {
  private ChatApplicationEntry() {}

  public static ConversationInterpreter.Interpretation resolve(String input) {
    var loan = LoanApplicationChatEntry.resolve(input);
    if (loan != null) return loan;
    String text = BankingLanguage.normalize(input);
    if (BankingLanguage.guarded(text)) return null;
    String prefix = "(?:please )?(?:(?:i (?:want|would like|need) to|i'd like to|can you|could you|help me) )?";
    String account = "(?:(?:a|an|the|my|new) )*(?:(?:savings|current|bank|nexa) )?account";
    if (text.matches(prefix + "(?:open|create|apply for|start) " + account + "(?: application)?(?: please)?")
        || text.matches("(?:new )?account application(?: form)?")
        || text.matches("(?:please )?(?:openaccount|createaccount)(?: please)?")
        || text.matches("(?:naya |नया )?(?:savings )?account (?:kholo|khol do|खोलो|खोलें)"))
      return entry("OPEN_ACCOUNT", "ACCOUNT_APPLICATION", "Account application.",
          "Complete the account application below. Review your details and opening amount before submitting it for bank review. "
              + "This message does not open an account or add money.");
    String mandate = "(?:(?:a|an|the|my|new) )*(?:mandate|direct debit)";
    if (text.matches(prefix + "(?:create|set up|setup|start|open|request) " + mandate
            + "(?: application| authorization)?(?: please)?")
        || text.matches("(?:new )?(?:mandate|direct debit) (?:application|authorization)(?: form)?"))
      return entry("CREATE_MANDATE", "MANDATE_APPLICATION", "Direct debit authorization.",
          "Complete the direct debit form below with a source account, recipient, limit and effective dates. "
              + "Submitting saves a pending mandate. Activation is a separate step and no money moves when you create it.");
    return null;
  }

  private static ConversationInterpreter.Interpretation entry(
      String intent, String type, String essence, String reply) {
    return new ConversationInterpreter.Interpretation(intent, essence, reply,
        new BankingContent(1, type, null, null, null, 0).describe(type, null, null));
  }
}
