package com.nexa.api.service;

import com.nexa.api.beans.BankingLanguage;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/** A conversational amount preference, resolved against current owned data before review. */
public record PaymentAmountRequest(String mode, BigDecimal percentage) {
  private static final Pattern PERCENT =
      Pattern.compile("(?<![\\p{L}\\p{N}.,])([+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+))\\s*(?:%|percent\\b|per cent\\b)");

  public static PaymentAmountRequest parse(String input, String operation) {
    String text = BankingLanguage.normalize(input);
    if (text.matches(".*\\b(show|check|list|view|tell|what|how|why|help)\\b.*")) return null;
    if (operation.equals("PAY_BILL")) {
      var match = PERCENT.matcher(text);
      if (match.find()) {
        BigDecimal value = new BigDecimal(match.group(1));
        if (match.find() || value.signum() <= 0 || value.compareTo(new BigDecimal("100")) > 0)
          return new PaymentAmountRequest("INVALID_PERCENT", null);
        return new PaymentAmountRequest("PERCENT", value);
      }
      if (text.contains("%") || text.matches(".*\\b(percent|percentage|per cent)\\b.*"))
        return new PaymentAmountRequest("INVALID_PERCENT", null);
      if (text.matches(".*\\bhalf\\b.*")) return new PaymentAmountRequest("PERCENT", new BigDecimal("50"));
      if (text.matches(".*\\bquarter\\b.*")) return new PaymentAmountRequest("PERCENT", new BigDecimal("25"));
    }
    if (operation.equals("PAY_CARD")) {
      if (text.matches(".*\\b(minimum|min)\\s+(?:amount\\s+)?(?:due|payment)\\b.*"))
        return new PaymentAmountRequest("MINIMUM", null);
      if (text.matches(".*\\b(full|entire|total)\\s+(?:(?:credit[- ]?)?card\\s+)?(?:outstanding|due|balance|bill)\\b.*")
          || text.matches(".*\\bclear\\b.*\\bbill\\b.*")
          || text.matches(".*\\b(?:pay|paid)\\b.*\\bin full\\b.*"))
        return new PaymentAmountRequest("FULL", null);
    }
    return null;
  }
}
