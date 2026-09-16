package com.nexa.api.transactions;

import com.nexa.api.core.model.TransactionStatus;
import com.nexa.api.shared.errors.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

/** Conservative parser: unsupported words are reported, never discarded before querying. */
@Component
public class NaturalLanguageTransactionQueryParser {
  private final BusinessDateResolver dates;
  public NaturalLanguageTransactionQueryParser(BusinessDateResolver dates) { this.dates = dates; }

  public TransactionQuery parseSpending(String input) {
    String text = input.toLowerCase(Locale.ROOT).replace('’', '\'');
    if (!text.matches(".*\\b(spend|spent|spending|transactions?|payments?)\\b.*")) return null;
    if (text.matches(".*\\b(in|at)\\s+(mumbai|delhi|bangalore|bengaluru)\\b.*"))
      throw new InvalidRequestException("Location filtering is not available because transactions do not store a location.");
    BusinessDateResolver.Range range = dateRange(text);
    if (range == null) throw new InvalidRequestException("Please include a supported date or date range; I will not substitute another period.");
    BigDecimal min = amount(text, "(?:above|over|more than|greater than|at least)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");
    BigDecimal max = amount(text, "(?:below|under|less than|at most)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");
    Matcher between = Pattern.compile("between\\s*(?:₹|rs\\.?|inr)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:and|to)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)").matcher(text);
    if (between.find()) { min = money(between.group(1)); max = money(between.group(2)); }
    BigDecimal exact = amount(text, "(?:exactly|equal to)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");
    if (exact != null) { min = exact; max = exact; }

    List<TransactionQuery.Condition> clauses = new ArrayList<>();
    List<String> merchants = terms(text, "(?:from|at)\\s+([a-z][a-z0-9 .&'-]*(?:\\s+or\\s+[a-z][a-z0-9 .&'-]*)?)(?=\\s+(?:but|except|excluding|above|below|over|under|last|this|yesterday|today)|$)");
    if (!merchants.isEmpty()) clauses.add(new TransactionQuery.Predicate(TransactionQuery.Field.MERCHANT, merchants.size() > 1 ? TransactionQuery.Operator.IN : TransactionQuery.Operator.EQ, merchants));
    List<String> excluded = terms(text, "(?:except|excluding|but not)\\s+([a-z][a-z0-9 .&'-]*?)(?=\\s+(?:above|below|over|under|last|this|yesterday|today)|$)");
    if (!excluded.isEmpty()) clauses.add(new TransactionQuery.Predicate(TransactionQuery.Field.CATEGORY, TransactionQuery.Operator.NOT_IN, excluded));
    if (text.matches(".*\\bfood\\b.*")) clauses.add(new TransactionQuery.Predicate(TransactionQuery.Field.CATEGORY, TransactionQuery.Operator.EQ, List.of("food")));
    if (text.matches(".*\\b(upi|credit card|debit card|cash)\\b.*")) {
      String method = text.contains("credit card") ? "credit card" : text.contains("debit card") ? "debit card" : text.contains("upi") ? "upi" : "cash";
      clauses.add(new TransactionQuery.Predicate(TransactionQuery.Field.PAYMENT_METHOD, TransactionQuery.Operator.EQ, List.of(method)));
    }
    if (text.matches(".*\\b(refund|refunded)\\b.*") && text.matches(".*\\b(except|excluding|but not)\\b.*"))
      clauses.add(new TransactionQuery.Predicate(TransactionQuery.Field.STATUS, TransactionQuery.Operator.NE, List.of("REFUNDED")));
    TransactionQuery.Condition condition = clauses.isEmpty() ? null : new TransactionQuery.Group(TransactionQuery.Join.AND, clauses);
    return TransactionQuery.spending(range, condition, min, max);
  }

  private BusinessDateResolver.Range dateRange(String text) {
    Map<String, BusinessDateResolver.RelativeDate> relative = Map.of(
        "today", BusinessDateResolver.RelativeDate.TODAY, "yesterday", BusinessDateResolver.RelativeDate.YESTERDAY,
        "this week", BusinessDateResolver.RelativeDate.THIS_WEEK, "last week", BusinessDateResolver.RelativeDate.LAST_WEEK,
        "this month", BusinessDateResolver.RelativeDate.THIS_MONTH, "last month", BusinessDateResolver.RelativeDate.LAST_MONTH,
        "this weekend", BusinessDateResolver.RelativeDate.THIS_WEEKEND, "last weekend", BusinessDateResolver.RelativeDate.LAST_WEEKEND);
    for (var entry : relative.entrySet()) if (text.contains(entry.getKey())) return dates.resolve(entry.getValue());
    Matcher iso = Pattern.compile("(?:from|between)\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(?:to|and)\\s+(\\d{4}-\\d{2}-\\d{2})").matcher(text);
    if (iso.find()) { LocalDate from = LocalDate.parse(iso.group(1)); return dates.range(from, LocalDate.parse(iso.group(2)).plusDays(1), from + " to " + iso.group(2)); }
    Matcher single = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b").matcher(text);
    if (single.find()) { LocalDate date = LocalDate.parse(single.group(1)); return dates.range(date, date.plusDays(1), date.toString()); }
    return null;
  }
  private static BigDecimal amount(String text, String expression) { Matcher m = Pattern.compile(expression).matcher(text); return m.find() ? money(m.group(1)) : null; }
  private static BigDecimal money(String text) { return new BigDecimal(text.replace(",", "")); }
  private static List<String> terms(String text, String pattern) {
    Matcher m = Pattern.compile(pattern).matcher(text); if (!m.find()) return List.of();
    return Arrays.stream(m.group(1).split("\\s+or\\s+"))
        .map(s -> s.replaceAll("\\s+(?:last|this) (?:month|week|weekend)$", "").trim())
        .filter(s -> !s.isBlank()).toList();
  }
}
