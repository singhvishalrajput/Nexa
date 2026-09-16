package com.nexa.api.service;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.BankingContent;


import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.SpendingQueryService;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ConversationInsights {
  private final SpendingQueryService spending;
  private final AccountQueryService accounts;
  private final com.nexa.api.service.TransactionQueryService transactions;
  private final com.nexa.api.service.NaturalLanguageTransactionQueryParser queryParser;

  public ConversationInsights(
      SpendingQueryService spending,
      AccountQueryService accounts,
      com.nexa.api.service.TransactionQueryService transactions,
      com.nexa.api.service.NaturalLanguageTransactionQueryParser queryParser) {
    this.spending = spending;
    this.accounts = accounts;
    this.transactions = transactions;
    this.queryParser = queryParser;
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
    var query = queryParser.parseSpending(text);
    if (query == null) return null;
    var result = transactions.query(query);
    var categories = new ArrayList<Map<String, String>>();
    var grouped = new LinkedHashMap<String, BigDecimal>();
    var currencies = new HashMap<String, String>();
    for (var row : result.transactions()) {
      String category = row.category() == null ? "Uncategorised" : row.category();
      String key = category + "\u0000" + row.currencyCode();
      grouped.merge(key, row.amount().abs(), BigDecimal::add);
      currencies.put(key, row.currencyCode());
    }
    for (var entry : grouped.entrySet()) {
      String category = entry.getKey().substring(0, entry.getKey().indexOf('\u0000'));
      categories.add(Map.of("category", category, "currency", currencies.get(entry.getKey()), "amount", entry.getValue().toPlainString()));
    }
    var scope = query.dateRange();
    var content =
        new BankingContent(1, "INSIGHTS", null, null, null, 0)
            .describe(
                "SPENDING_SUMMARY",
                null,
                Map.of(
                    "categories",
                    categories,
                    "from", scope.startDate().toString(),
                    "to", scope.endDateInclusive().toString(),
                    "query", query,
                    "resolvedScope", Map.of("start", scope.start().toString(), "end", scope.end().toString(), "timezone", scope.start().getZone().getId())));
    return new ConversationInterpreter.Interpretation(
        "GET_SPENDING",
        "Monthly spending.",
        result.transactions().isEmpty()
            ? "No matching completed outgoing transactions were recorded for this period."
            : "Here is your recorded spending by category. This includes withdrawals and excludes"
                  + " transfers between your own accounts. Uncategorised payments have no recorded"
                  + " category.",
        content);
  }
}
