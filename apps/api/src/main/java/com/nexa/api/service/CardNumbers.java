package com.nexa.api.service;

import java.util.Locale;

/** Internal Nexa card identifiers, not externally issued payment-network credentials. */
public final class CardNumbers {
  private CardNumbers() {}
  public static String forAccount(long id, String previousMask) {
    if (id < 1 || id > 999999999L) throw new IllegalArgumentException("Card identifier range exceeded");
    String digits = previousMask == null ? "" : previousMask.replaceAll("[^0-9]", "");
    String suffix = digits.length() >= 4 ? digits.substring(digits.length() - 4)
        : String.format(Locale.ROOT, "%04d", new java.security.SecureRandom().nextInt(10000));
    return "990" + String.format(Locale.ROOT, "%09d", id) + suffix;
  }
}
