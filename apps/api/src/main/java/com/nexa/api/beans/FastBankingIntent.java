package com.nexa.api.beans;


import java.util.Locale;

/** Complete, self-contained read requests only; never discard unrecognized words or filters. */
public final class FastBankingIntent {
  private FastBankingIntent() {}

  public static Intent match(String input) {
    String text =
        input
            .toLowerCase(Locale.ROOT)
            .replace('’', '\'')
            .trim()
            .replaceAll("[.!?]+$", "")
            .replaceAll("\\s+", " ")
            .trim();
    text =
        text.replaceFirst("^please ", "")
            .replaceFirst("^(?:(?:can|could|would) you )?(?:show|tell|give)(?: me)? ", "")
            .replaceFirst("^(?:check|list|view) ", "")
            .replaceFirst("^(?:what is|what's|what are) ", "")
            .replaceFirst(" please$", "");
    if (text.matches(
        "(?:my |the )?(?:(?:available|account|savings|savings account|current account)"
            + " )?balances?"))
      return Intent.GET_BALANCE;
    if (text.matches("(?:my |the )?(?:(?:savings|current) )?accounts?")) return Intent.GET_ACCOUNTS;
    if (text.matches("(?:my |the )?(?:(?:recent|latest) )?transactions?"))
      return Intent.GET_RECENT_TRANSACTIONS;
    if (text.matches("(?:my |the )?bills")) return Intent.GET_BILLS;
    if (text.matches("(?:my |the )?credit cards")) return Intent.GET_CREDIT_CARDS;
    if (text.matches("(?:my |the )?cards")) return Intent.GET_CARDS;
    if (text.matches("(?:my |the )?beneficiaries")) return Intent.GET_BENEFICIARIES;
    if (text.matches("(?:my |the )?mandates")) return Intent.GET_MANDATES;
    if (text.matches("(?:my |the )?scheduled payments")) return Intent.GET_SCHEDULED_PAYMENTS;
    if (text.matches("(?:my |the )?loans")) return Intent.GET_LOANS;
    return null;
  }
}
