package com.nexa.api.conversations;

import com.nexa.api.accounts.AccountQueryService;
import com.nexa.api.banking.SpendingQueryService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ConversationInsights {
  private final SpendingQueryService spending;
  private final AccountQueryService accounts;
  private final com.nexa.api.transactions.TransactionQueryService transactions;

  public ConversationInsights(
      SpendingQueryService spending,
      AccountQueryService accounts,
      com.nexa.api.transactions.TransactionQueryService transactions) {
    this.spending = spending;
    this.accounts = accounts;
    this.transactions = transactions;
  }

  public ConversationInterpreter.Interpretation interpret(String text) {
    String t = text.toLowerCase(Locale.ROOT);
    if (t.matches(".*\\b(salary|payroll)\\b.*")) {
      var owned = accounts.currentAccounts();
      if (owned.isEmpty())
        return new ConversationInterpreter.Interpretation(
            "GET_SALARY", "Check incoming payments.", "You do not have a Nexa account yet.");
      var account = owned.get(0);
      LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
      if (t.contains("last month")) month = month.minusMonths(1);
      var credits =
          transactions.transactions(
              account.id(), null, month, month.plusMonths(1).minusDays(1), 0, 100, "CREDIT", null);
      return new ConversationInterpreter.Interpretation(
          "GET_SALARY",
          "Check incoming payments.",
          "Here are incoming payments for "
              + account.displayName()
              + " for "
              + month.getMonth()
              + ". Check the sender to identify your salary; payroll verification is not available."
              + " For other accounts or more results, open transaction history.",
          BankingContent.recent(account, credits.content(), credits.totalElements()));
    }
    if (t.matches(".*\\b(declined|decline)\\b.*"))
      return new ConversationInterpreter.Interpretation(
          "DECLINE_HELP",
          "Check declined transaction.",
          "The bank does not provide decline reasons in these records. Open the transaction details"
              + " to check its recorded status and reference, then contact your bank for the"
              + " reason.");
    if (t.contains("safely") && t.contains("transfer"))
      return new ConversationInterpreter.Interpretation(
          "GET_BALANCE",
          "Available transfer balance.",
          "These are your available balances, not a safe-to-spend recommendation. Allow for"
              + " upcoming bills and your own buffer before choosing an amount.",
          BankingContent.balances(accounts.currentAccounts()));
    if (!t.matches(".*\\b(spend|spent|spending)\\b.*")) return null;
    if (!t.contains("this month") && !t.contains("last month"))
      return new ConversationInterpreter.Interpretation(
          "GET_SPENDING",
          "Spending period.",
          "Would you like spending for this month or last month? Include the period in your"
              + " request.");
    LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    if (t.contains("last month")) month = month.minusMonths(1);
    String category = t.matches(".*\\bfood\\b.*") ? "food" : null;
    var categories = spending.spending(month, month.plusMonths(1), category);
    var content =
        new BankingContent(1, "INSIGHTS", null, null, null, 0)
            .describe(
                "SPENDING_SUMMARY",
                null,
                Map.of(
                    "categories",
                    categories,
                    "from",
                    month.toString(),
                    "to",
                    month.plusMonths(1).minusDays(1).toString()));
    return new ConversationInterpreter.Interpretation(
        "GET_SPENDING",
        "Monthly spending.",
        categories.isEmpty()
            ? "No matching completed outgoing transactions were recorded for this period."
            : "Here is your recorded spending by category. This includes withdrawals and excludes"
                  + " transfers between your own accounts. Uncategorised payments have no recorded"
                  + " category.",
        content);
  }
}
