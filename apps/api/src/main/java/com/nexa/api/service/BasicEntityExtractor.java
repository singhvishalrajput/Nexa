package com.nexa.api.service;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.BankingLanguage;
import com.nexa.api.beans.Intent;
import com.nexa.api.exep.InvalidRequestException;


import com.nexa.api.exep.InvalidRequestException;
import java.math.BigDecimal;
import java.time.*;
import java.util.Locale;
import java.util.regex.*;

public class BasicEntityExtractor implements EntityExtractor {
  private final Clock clock;

  public BasicEntityExtractor(Clock clock) {
    this.clock = clock;
  }

  private String match(String text, String regex) {
    var m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
    return m.find() ? m.group(1) : null;
  }

  public Entities extract(String input, Intent intent) {
    String text = BankingLanguage.normalize(input);
    String amount = match(text, "(?:₹|rs\\.?|inr|amount)\\s*([0-9,]+(?:\\.[0-9]+)?)");
    if (amount == null
        && (intent == Intent.START_TRANSFER
            || intent == Intent.PAY_BILL
            || intent == Intent.PAY_CARD))
      amount = match(text, "(?:send|transfer|pay)\\s+([0-9,]+(?:\\.[0-9]+)?)");
    if (amount == null) amount = match(text, "([0-9,]+(?:\\.[0-9]+)?)\\s*(?:rupees?|inr|rs)\\b");
    String account = match(input, "\\b(acc_[a-z0-9_]+)\\b");
    if (account == null) account = match(input, "\\baccount (?:id )?([0-9]+)\\b");
    if (Pattern.compile("\\bacc_[a-z0-9_]+\\b", Pattern.CASE_INSENSITIVE)
            .matcher(input)
            .results()
            .count()
        > 1) throw new InvalidRequestException("Please select one source account.");
    String target = match(input, "\\b((?:txn|mnd|bil|crd|ben|sch|lon|trf)_[a-z0-9_]+)\\b");
    if (target == null) target = match(input, "\\b(TX-[a-f0-9]{32})\\b");
    var references =
        Pattern.compile(
                "\\b(?:txn|mnd|bil|crd|ben|sch|lon|trf)_[a-z0-9_]+\\b", Pattern.CASE_INSENSITIVE)
            .matcher(input);
    if (references.results().count() > 1)
      throw new InvalidRequestException("Please ask about one target reference at a time.");
    String payee = match(input, "\\bto ([\\p{L} '-]+)$");
    LocalDate from = null, to = null;
    if (text.contains("this month")) {
      var month = LocalDate.now(clock);
      from = month.withDayOfMonth(1);
      to = month.withDayOfMonth(month.lengthOfMonth());
    }
    if (text.contains("this week")) {
      from =
          LocalDate.now(clock)
              .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      to = from.plusDays(6);
    }
    if (text.contains("yesterday")) {
      from = LocalDate.now(clock).minusDays(1);
      to = from;
    }
    if (text.contains("last week")) {
      to = LocalDate.now(clock).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1);
      from = to.minusDays(6);
    }
    if (text.contains("this weekend")) {
      from = LocalDate.now(clock).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
      to = from.plusDays(1);
    }
    if (text.contains("last weekend")) {
      to = LocalDate.now(clock).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).minusDays(1);
      from = to.minusDays(1);
    }
    if (text.contains("last month")) {
      var month = LocalDate.now(clock).minusMonths(1);
      from = month.withDayOfMonth(1);
      to = month.withDayOfMonth(month.lengthOfMonth());
    }
    String first = match(text, "from (\\d{4}-\\d{2}-\\d{2})"),
        last = match(text, "to (\\d{4}-\\d{2}-\\d{2})");
    try {
      if (first != null) from = LocalDate.parse(first);
      if (last != null) to = LocalDate.parse(last);
    } catch (DateTimeException ex) {
      throw new InvalidRequestException("Use valid dates in YYYY-MM-DD format.");
    }
    String status =
        match(
            text,
            "\\b(active|paused|cancelled|expired|action_required|due|overdue|upcoming|paid|failed)\\b");
    return new Entities(
        amount == null ? null : new BigDecimal(amount.replace(",", "")),
        account,
        target,
        payee,
        from,
        to,
        text.contains("saving") ? "SAVINGS" : text.contains("current account") ? "CURRENT" : null,
        text.contains("debit") ? "DEBIT" : text.contains("credit transaction") ? "CREDIT" : null,
        match(input, "search [\"']([^\"']+)[\"']"),
        status == null ? null : status.toUpperCase(Locale.ROOT));
  }
}
