package com.nexa.api.service;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.exep.InvalidRequestException;


import com.nexa.api.service.AccountQueryService;
import com.nexa.api.beans.BankingModels.PreparedAction;
import com.nexa.api.service.BeneficiaryQueryService;
import com.nexa.api.exep.InvalidRequestException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class ActionPreparationService {
  private final AccountQueryService accounts;
  private final BeneficiaryQueryService beneficiaries;
  private final BillQueryService bills;
  private final MandateQueryService mandates;
  private final CardQueryService cards;

  public ActionPreparationService(
      AccountQueryService accounts,
      BeneficiaryQueryService beneficiaries,
      BillQueryService bills,
      MandateQueryService mandates,
      CardQueryService cards) {
    this.accounts = accounts;
    this.beneficiaries = beneficiaries;
    this.bills = bills;
    this.mandates = mandates;
    this.cards = cards;
  }

  public PreparedAction prepare(
      String operation, String accountId, String targetId, BigDecimal amount) {
    if (targetId == null || accountId == null)
      throw new InvalidRequestException(
          "Provide the source account ID and target ID to prepare this action.");
    var account = accounts.requireOwnedAccount(accountId);
    if (!"ACTIVE".equals(account.status()))
      throw new InvalidRequestException("The source account is not active.");
    String currency = account.currencyCode();
    switch (operation) {
      case "START_TRANSFER" -> {
        if (!"ACTIVE".equals(beneficiaries.detail(targetId).status()))
          throw new InvalidRequestException("The beneficiary is not active.");
      }
      case "PAY_BILL" -> {
        var bill = bills.detail(targetId);
        if (!java.util.Set.of("UPCOMING", "DUE", "OVERDUE", "FAILED").contains(bill.status()))
          throw new InvalidRequestException("This bill is not payable.");
        if (!currency.equals(bill.currencyCode()))
          throw new InvalidRequestException("Currencies do not match.");
        if (amount == null) amount = new BigDecimal(bill.amount());
        if (amount.compareTo(new BigDecimal(bill.amount())) > 0
            || bill.minimumAmount() != null
                && amount.compareTo(new BigDecimal(bill.minimumAmount())) < 0)
          throw new InvalidRequestException("The amount is outside the bill payment range.");
      }
      case "PAY_CARD" -> {
        var card = cards.detail(targetId);
        if (!"ACTIVE".equals(card.status()) || !"CREDIT".equals(card.cardType()))
          throw new InvalidRequestException("This card cannot receive a payment.");
        if (!currency.equals(card.currencyCode()))
          throw new InvalidRequestException("Currencies do not match.");
        if (amount == null) amount = new BigDecimal(card.outstanding());
        if (amount.compareTo(new BigDecimal(card.outstanding())) > 0)
          throw new InvalidRequestException("The amount exceeds the outstanding balance.");
      }
      case "CANCEL_MANDATE" -> {
        var mandate = mandates.detail(targetId);
        if (!accountId.equals(mandate.accountId()))
          throw new InvalidRequestException("Use the mandate's linked account.");
        if (!java.util.Set.of("ACTIVE", "PAUSED", "ACTION_REQUIRED").contains(mandate.status()))
          throw new InvalidRequestException("This mandate cannot be cancelled.");
        return new PreparedAction(
            operation, "PREPARED", accountId, targetId, null, currency, true, false);
      }
      default -> throw new InvalidRequestException("Unsupported action.");
    }
    if (amount == null
        || amount.signum() <= 0
        || amount.scale() > 2
        || amount.precision() - amount.scale() > 13)
      throw new InvalidRequestException(
          "Provide a positive amount with at most two decimal places.");
    if (amount.compareTo(account.availableBalance()) > 0)
      throw new InvalidRequestException("The available balance is insufficient.");
    return new PreparedAction(
        operation, "PREPARED", accountId, targetId, amount.toPlainString(), currency, true, false);
  }
}
