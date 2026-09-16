package com.nexa.api.service;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountType;
import com.nexa.api.beans.BankingContent;
import com.nexa.api.beans.Intent;

import com.nexa.api.service.BeneficiaryQueryService;
import com.nexa.api.service.TransferQueryService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BankingDomainRouter implements DomainRouter {
  private final AccountQueryService accounts;
  private final TransactionQueryService transactions;
  private final MandateQueryService mandates;
  private final BillQueryService bills;
  private final CardQueryService cards;
  private final BeneficiaryQueryService beneficiaries;
  private final ScheduledPaymentQueryService scheduled;
  private final LoanQueryService loans;
  private final ActionPreparationService actions;
  private final TransferQueryService transfers;

  public BankingDomainRouter(
      AccountQueryService accounts,
      TransactionQueryService transactions,
      MandateQueryService mandates,
      BillQueryService bills,
      CardQueryService cards,
      BeneficiaryQueryService beneficiaries,
      ScheduledPaymentQueryService scheduled,
      LoanQueryService loans,
      ActionPreparationService actions,
      TransferQueryService transfers) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.mandates = mandates;
    this.bills = bills;
    this.cards = cards;
    this.beneficiaries = beneficiaries;
    this.scheduled = scheduled;
    this.loans = loans;
    this.actions = actions;
    this.transfers = transfers;
  }

  private Reply result(String type, String message, BankingContent content) {
    return new Reply(type, message, content, null);
  }

  private Reply domain(String type, String view, String message, Object data) {
    return result(type, message, BankingContent.domain(view, type, data));
  }

  private Reply reference() {
    return new Reply(
        "ACTION_REQUIRED",
        "Which item would you like to see? Choose one from the list.",
        null,
        "REFERENCE_REQUIRED");
  }

  public Reply route(Intent intent, EntityExtractor.Entities e) {
    return switch (intent) {
      case HELP ->
          result(
              "TEXT",
              "Ask about balances, accounts, transactions, mandates, bills, cards, beneficiaries,"
                  + " scheduled payments or loans. Ask to transfer between your own accounts, or"
                  + " prepare a beneficiary or bill payment for review.",
              null);
      case UNKNOWN ->
          new Reply(
              "TEXT",
              "I am not sure I understood. Try asking for balances, transactions, mandates or"
                  + " bills.",
              null,
              "LOW_CONFIDENCE_INTENT");
      case GET_BALANCE, GET_ACCOUNTS -> {
        if (e.from() != null || e.to() != null)
          yield new Reply(
              "ACTION_REQUIRED",
              "Historical balances are not available. Please ask for your current balance.",
              null,
              "UNSUPPORTED_FILTER");
        var owned =
            e.accountId() != null
                ? List.of(accounts.requireOwnedAccount(e.accountId()))
                : accounts.currentAccounts();
        if (e.accountType() != null)
          owned = owned.stream().filter(a -> e.accountType().equals(a.accountType())).toList();
        yield result(
            "ACCOUNT_LIST",
            owned.isEmpty()
                ? "You do not have a matching Nexa account yet."
                : "Here are your available balances.",
            BankingContent.balances(owned));
      }
      case GET_RECENT_TRANSACTIONS -> {
        var owned =
            e.accountId() != null
                ? List.of(accounts.requireOwnedAccount(e.accountId()))
                : accounts.currentAccounts();
        if (e.accountType() != null)
          owned = owned.stream().filter(a -> e.accountType().equals(a.accountType())).toList();
        if (owned.isEmpty())
          yield result("TEXT", "You do not have a matching Nexa account yet.", null);
        var account = owned.get(0);
        var recent =
            e.direction() == null && e.search() == null
                ? transactions.transactions(account.id(), null, e.from(), e.to(), 0, 5)
                : transactions.transactions(
                    account.id(), null, e.from(), e.to(), 0, 5, e.direction(), e.search());
        yield result(
            "TRANSACTION_LIST",
            "Here are your latest transactions.",
            BankingContent.recent(account, recent.content(), recent.totalElements()));
      }
      case GET_TRANSACTION_DETAIL -> {
        if (e.targetId() == null) yield reference();
        var item = transactions.detail(e.targetId());
        yield result(
            "TRANSACTION_DETAIL",
            "Here are the transaction details.",
            BankingContent.recent(
                accounts.requireOwnedAccount(item.accountId()), List.of(item), 1));
      }
      case GET_MANDATES ->
          domain(
              "MANDATE_LIST",
              "MANDATES",
              "Here are your mandates.",
              mandates.list(e.status(), 0, 30));
      case GET_MANDATE_DETAIL ->
          e.targetId() == null
              ? reference()
              : domain(
                  "MANDATE_DETAIL",
                  "MANDATES",
                  "Here are the mandate details.",
                  List.of(mandates.detail(e.targetId())));
      case GET_BILLS ->
          domain(
              "BILL_LIST",
              "BILLS",
              e.from() == null && e.to() == null
                  ? "Here are your bills and their payment statuses."
                  : "Here are recorded bills due in the requested period (from the first 100"
                      + " bills).",
              e.from() == null && e.to() == null
                  ? bills.list(e.status(), 0, 30)
                  : bills.list(null, 0, 100).stream().filter(b -> inPeriod(b.dueAt(), e)).toList());
      case GET_BILL_DETAIL ->
          e.targetId() == null
              ? reference()
              : domain(
                  "BILL_DETAIL",
                  "BILLS",
                  "Here are the bill details.",
                  List.of(bills.detail(e.targetId())));
      case GET_CREDIT_CARDS ->
          domain(
              "CREDIT_CARD_LIST",
              "CARDS",
              "Here are your credit cards.",
              cards.creditCards(null, 0, 30));
      case GET_CARDS ->
          domain("CARD_LIST", "CARDS", "Here are your cards.", cards.list(null, 0, 30));
      case GET_CARD_TRANSACTIONS -> {
        var owned =
            e.targetId() == null ? cards.list(null, 0, 30) : List.of(cards.detail(e.targetId()));
        if (owned.isEmpty())
          yield domain("CREDIT_CARD_LIST", "CARDS", "There are no cards to show.", owned);
        var card = owned.get(0);
        var items =
            card.transactions() == null
                ? List.<BankingContent.Transaction>of()
                : card.transactions();
        yield result(
            "CARD_TRANSACTION_LIST",
            "Here are transactions for " + card.displayName() + ".",
            new BankingContent(
                1,
                "TRANSACTIONS",
                null,
                BankingContent.Account.from(accounts.requireOwnedAccount(card.accountId())),
                items,
                items.size()));
      }
      case GET_BENEFICIARIES ->
          domain(
              "BENEFICIARY_LIST",
              "BENEFICIARIES",
              "Here are your beneficiaries.",
              beneficiaries.list());
      case GET_SCHEDULED_PAYMENTS ->
          domain(
              "SCHEDULED_PAYMENT_LIST",
              "SCHEDULED_PAYMENTS",
              "Here are your scheduled payments.",
              scheduled.list(null, 0, 30));
      case GET_LOANS ->
          domain("LOAN_SUMMARY", "LOANS", "Here are your loans and EMIs.", loans.list(null, 0, 30));
      case GET_LOAN_DETAIL ->
          e.targetId() == null
              ? reference()
              : domain(
                  "LOAN_SUMMARY",
                  "LOANS",
                  "Here are the loan details.",
                  List.of(loans.detail(e.targetId())));
      case TRANSFER_STATUS ->
          e.targetId() == null
              ? reference()
              : domain(
                  "TRANSFER_STATUS",
                  "TRANSFER_STATUS",
                  "Here is the recorded transfer status.",
                  transfers.detail(e.targetId()));
      case START_TRANSFER, PAY_BILL, PAY_CARD, CANCEL_MANDATE -> {
        if (e.accountId() == null || e.targetId() == null)
          yield new Reply(
              "ACTION_REQUIRED",
              e.targetId() == null
                  ? intent == Intent.PAY_BILL
                      ? "Which bill would you like to pay?"
                      : intent == Intent.PAY_CARD
                          ? "Which card would you like to pay?"
                          : intent == Intent.CANCEL_MANDATE
                              ? "Which direct debit would you like to cancel?"
                              : "Who would you like to pay?"
                  : "Which account should the money come from?",
              null,
              "ACTION_DETAILS_REQUIRED");
        yield domain(
            "ACTION_REQUIRED",
            "ACTION_REQUIRED",
            "Please check the payment details before confirming.",
            actions.prepare(intent.name(), e.accountId(), e.targetId(), e.amount()));
      }
    };
  }

  private boolean inPeriod(String date, EntityExtractor.Entities e) {
    if (date == null || date.length() < 10) return false;
    try {
      var day = java.time.LocalDate.parse(date.substring(0, 10));
      return (e.from() == null || !day.isBefore(e.from()))
          && (e.to() == null || !day.isAfter(e.to()));
    } catch (java.time.DateTimeException ex) {
      return false;
    }
  }
}
