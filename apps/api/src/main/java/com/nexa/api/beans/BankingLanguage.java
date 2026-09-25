package com.nexa.api.beans;

import java.util.Locale;

/** Shared normalization for routing and slot matching; never used to authorize a posting. */
public final class BankingLanguage {
  private BankingLanguage() {}

  public static String normalize(String input) {
    String text = input.toLowerCase(Locale.ROOT).replace('’', '\'');
    for (int i = 0; i < 10; i++) text = text.replace((char) ('०' + i), (char) ('0' + i));
    String[][] aliases = {
      {"बिजली", "electricity"},
      {"बिल", "bill"},
      {"भुगतान", "pay"},
      {"बैलेंस", "balance"},
      {"शेष राशि", "balance"},
      {"खातों", "accounts"},
      {"खाते", "account"},
      {"खाता", "account"},
      {"बचत", "savings"},
      {"पैसे", "money"},
      {"पैसा", "money"},
      {"भेजो", "send"},
      {"भेजें", "send"},
      {"लेनदेन", "transactions"},
      {"लाभार्थी", "beneficiary"},
      {"कार्ड", "card"},
      {"दिखाओ", "show"},
      {"दिखाइए", "show"},
      {"बताओ", "show"},
      {"बताइए", "show"},
      {"रद्द", "cancel"},
      {"नहीं", "nahi"},
      {"नही", "nahi"},
      {"हाँ", "haan"},
      {"हां", "haan"},
      {"कर दो", "kar do"},
      {"करो", "karo"},
      {"कर दें", "kar do"},
      {"करना", "karna"},
      {"रुपये", "inr"},
      {"रुपए", "inr"},
      {"से", "se"},
      {"को", "ko"},
      {"अपने", "apne"},
      {"बीच", "between"},
      {"दिखा दो", "show"},
      {"जमा", "pay"}
    };
    for (var alias : aliases) text = text.replace(alias[0], alias[1]);
    return text.replaceAll("\\b(bijli|bijlee|power)\\b", "electricity")
        .replaceAll("\\b(paisa|paise|pese)\\b", "money")
        .replaceAll("\\b(bhejo|bhejdo|bhej|bhejiye)\\b", "send")
        .replaceAll("\\b(batao|bata|bataiye|dikhao|dikhaiye)\\b", "show")
        .replaceAll("[!?।]", " ")
        .replaceAll("\\s+", " ")
        .trim()
        .replaceAll("[.]+$", "");
  }

  public static boolean cancel(String input) {
    return normalize(input)
            .matches(
                "(?:please )?(?:(?:no|nahi|nahin|nhi) )?(?:cancel(?: it| that| this| payment|"
                    + " transfer)?(?: karo| kar do)?|stop|never mind|nevermind|rehne do|mat"
                    + " karo|don't do it|do not proceed)")
        || normalize(input).matches("no|nahi|nahin|nhi");
  }

  public static boolean continuation(String input) {
    return normalize(input)
        .matches(
            "(?:please )?(yes|yeah|yep|haan|ha|han|ji|ok|okay|confirm|proceed|continue|do it|go"
                + " ahead|show it|show that|pay it|pay the bill|pay karo|bill pay karo|pay bill|kar"
                + " do|karo|haan kar do|haan karo|yes please|send it|wahi|usi ko|इसे दिखाओ|अभी"
                + " करो)");
  }

  public static boolean guarded(String input) {
    return normalize(input)
        .matches(
            ".*\\b(dont|don't|not|never|unless|if|and|then|tomorrow|every|nahi|nahin|nhi|mat|kal|agar)\\b.*");
  }

  public static boolean readRequest(String input) {
    if (cardPaymentShortcut(normalize(input))) return false;
    return normalize(input)
        .matches(
            ".*\\b(show|check|list|view|tell|what|how"
                + " much|balance|history|transactions|status|details|outstanding)\\b.*");
  }

  public static String operation(String input) {
    String text = normalize(input);
    if (guarded(text) || readRequest(text) || text.matches(".*\\b(how|why|help|safely)\\b.*"))
      return null;
    if (cardPaymentShortcut(text)) return "PAY_CARD";
    if (text.matches(".*\\b(cancel|stop)\\b.*\\b(mandate|autopay|direct debit)\\b.*"))
      return "CANCEL_MANDATE";
    if (text.matches(".*\\b(pay|make)\\b.*\\bemi\\b.*")
        || text.matches(".*\\b(repay|pay|repayment)\\b.*\\bloan\\b.*")
        || text.matches(".*\\bloan\\b.*\\b(repay|repayment|pay)\\b.*")) return "REPAY_LOAN";
    if (text.matches(".*\\bbill\\b.*\\b(with|using|from)\\b.*\\bcard\\b.*") && text.matches(".*\\b(pay|payment)\\b.*")) return "PAY_BILL";
    if (text.matches(".*\\bcard\\b.*")) {
      if (text.matches(".*\\bunfreeze\\b.*")) return "UNFREEZE_CARD";
      if (text.matches(".*\\b(freeze|block)\\b.*")) return "FREEZE_CARD";
      if (text.matches(".*\\breplace\\b.*")) return "REPLACE_CARD";
      if (text.matches(".*\\b(pay|repay|payment|repayment)\\b.*")) return "PAY_CARD";
    }
    if (text.matches(".*\\bbill\\b.*") && text.matches(".*\\b(pay|payment|bhar|bharo)\\b.*"))
      return "PAY_BILL";
    if (text.matches(".*\\b(send|transfer|move)\\b.*"))
      return text.matches(".*\\b(my accounts|own accounts|between accounts|apne accounts)\\b.*")
          ? "OWN_TRANSFER"
          : "START_TRANSFER";
    return null;
  }

  private static boolean cardPaymentShortcut(String text) {
    // Outstanding is normally a read keyword; a direct repayment request is an action.
    return !text.matches(".*\\b(show|check|list|view|tell|what|how|why|help)\\b.*")
        && (text.matches(".*\\b(?:pay|repay|clear)\\b.*\\b(?:credit[- ]?card|card)\\b.*\\b(?:outstanding|due|bill|balance)\\b.*")
            || text.matches(".*\\bclear (?:my |the )?bill\\b.*"));
  }

  public static Intent intent(String input) {
    String text = normalize(input);
    if (guarded(text)) return null;
    String operation = operation(text);
    if (operation != null
        && !operation.endsWith("FREEZE_CARD")
        && !operation.equals("REPLACE_CARD"))
      return operation.equals("REPAY_LOAN")
          ? Intent.GET_LOANS
          : operation.equals("OWN_TRANSFER") ? Intent.START_TRANSFER : Intent.valueOf(operation);
    if (text.matches(".*\\b(balance)\\b.*") && !text.contains("transfer"))
      return Intent.GET_BALANCE;
    if (text.matches(".*\\b(transactions|history)\\b.*")
        && !text.contains("card")
        && !text.contains("details")) return Intent.GET_RECENT_TRANSACTIONS;
    if (readRequest(text)) {
      if (text.matches(".*\\b(beneficiaries|beneficiary|payees|payee)\\b.*"))
        return Intent.GET_BENEFICIARIES;
      if (text.matches(".*\\bbills?\\b.*") && !text.contains("details")) return Intent.GET_BILLS;
      if (text.matches(".*\\baccounts?\\b.*")) return Intent.GET_ACCOUNTS;
    }
    return null;
  }
}
