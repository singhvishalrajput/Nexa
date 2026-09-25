package com.nexa.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CardSupport {
  private final String phone;
  private final String email;
  public CardSupport(@Value("${nexa.cards.support-phone:88000 00067}") String phone,
      @Value("${nexa.cards.support-email:nexa@help.bank.in}") String email) {
    this.phone = phone.trim(); this.email = email.trim();
  }
  public String phone() { return phone; }
  public String email() { return email; }
  public String guidance() {
    return phone.isBlank() ? "Contact your bank's card support for upgrades, adjustments or credit-card issuance."
        : "Call " + phone + (email.isBlank() ? "" : " or email " + email) + " for card upgrades, adjustments or credit-card issuance.";
  }
}
