package com.nexa.api.service;

import com.nexa.api.beans.BankingContent;
import com.nexa.api.beans.BankingLanguage;

/** A display-only entry to the existing authenticated multipart application, never a loan write. */
public final class LoanApplicationChatEntry {
  private LoanApplicationChatEntry() {}

  public static ConversationInterpreter.Interpretation resolve(String input) {
    String text = BankingLanguage.normalize(input)
        .replaceAll("\\bapplyforloan\\b", "apply for loan")
        .replace("ऋण", "loan").replace("लोन", "loan").replace("आवेदन", "apply");
    if (BankingLanguage.guarded(text)) return null;
    String loan = "(?:(?:a|an|the|my|new) )*(?:(?:personal|home|car|education|business|other) )?loan";
    boolean request = text.matches("(?:please )?(?:(?:i (?:want|would like|need) to|i'd like to|can you|could you|help me) )?"
        + "(?:apply(?: for)?|request|start|create|open) " + loan + "(?: application)?(?: please)?")
        || text.matches("(?:please )?(?:i (?:want|need|would like)|i'd like) " + loan + "(?: please)?")
        || text.matches("(?:new )?loan application(?: form)?")
        || text.matches("loan (?:ke liye |के लिए )?apply(?: karo| kar do| karna hai| करना है| करें)?")
        || text.matches("(?:mujhe|मुझे) loan (?:chahiye|चाहिए)");
    if (!request) return null;
    return new ConversationInterpreter.Interpretation(
        "APPLY_LOAN", "Loan application.",
        "Complete the loan application below and upload the three required monthly salary slips. "
            + "Your application goes for review when you submit it. No money moves until approval and your acceptance.",
        new BankingContent(1, "LOAN_APPLICATION", null, null, null, 0)
            .describe("LOAN_APPLICATION", null, null));
  }
}
