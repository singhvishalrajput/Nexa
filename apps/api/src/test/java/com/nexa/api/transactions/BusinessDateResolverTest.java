package com.nexa.api.transactions;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

class BusinessDateResolverTest {
  private final BusinessDateResolver resolver = new BusinessDateResolver(
      Clock.fixed(Instant.parse("2026-09-16T06:00:00Z"), ZoneOffset.UTC), ZoneId.of("Asia/Kolkata"));

  @Test
  void regressionYesterdayUsesKolkataMidnightBoundaries() {
    var range = resolver.resolve(BusinessDateResolver.RelativeDate.YESTERDAY);
    assertThat(range.start()).isEqualTo(ZonedDateTime.parse("2026-09-15T00:00:00+05:30[Asia/Kolkata]"));
    assertThat(range.end()).isEqualTo(ZonedDateTime.parse("2026-09-16T00:00:00+05:30[Asia/Kolkata]"));
    assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 9, 15));
  }

  @Test
  void resolvesWeeksMonthsAndWeekendsWithExclusiveEnds() {
    assertThat(resolver.resolve(BusinessDateResolver.RelativeDate.TODAY).endDateInclusive()).isEqualTo(LocalDate.of(2026, 9, 16));
    assertThat(resolver.resolve(BusinessDateResolver.RelativeDate.THIS_WEEK).startDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    assertThat(resolver.resolve(BusinessDateResolver.RelativeDate.LAST_WEEK).startDate()).isEqualTo(LocalDate.of(2026, 9, 7));
    assertThat(resolver.resolve(BusinessDateResolver.RelativeDate.THIS_MONTH).startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(resolver.resolve(BusinessDateResolver.RelativeDate.LAST_MONTH).endDateInclusive()).isEqualTo(LocalDate.of(2026, 8, 31));
    assertThat(resolver.resolve(BusinessDateResolver.RelativeDate.THIS_WEEKEND).startDate()).isEqualTo(LocalDate.of(2026, 9, 19));
    assertThat(resolver.resolve(BusinessDateResolver.RelativeDate.LAST_WEEKEND).startDate()).isEqualTo(LocalDate.of(2026, 9, 12));
  }

  @Test
  void parserRetainsDateAmountMerchantAndCategoryConstraints() {
    var parser = new NaturalLanguageTransactionQueryParser(resolver);
    var query = parser.parseSpending("What did I spend yesterday at Amazon above ₹2,000 on food?");
    assertThat(query.dateRange().startDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(query.minAmount()).isEqualByComparingTo("2000");
    assertThat(query.direction()).isEqualTo(TransactionQuery.Direction.OUTGOING);
    assertThat(query.statuses()).containsExactly(com.nexa.api.core.model.TransactionStatus.SUCCESS);
    assertThat(query.condition()).isInstanceOf(TransactionQuery.Group.class);
  }

  @Test
  void parserRepresentsMerchantAlternativesAndRejectsUnsupportedLocation() {
    var parser = new NaturalLanguageTransactionQueryParser(resolver);
    var query = parser.parseSpending("Show spending at Amazon or Flipkart last month");
    var group = (TransactionQuery.Group) query.condition();
    var merchant = (TransactionQuery.Predicate) group.children().get(0);
    assertThat(merchant.values()).containsExactly("amazon", "flipkart");
    assertThat(merchant.operator()).isEqualTo(TransactionQuery.Operator.IN);
    assertThatThrownBy(() -> parser.parseSpending("What did I spend in Mumbai yesterday"))
        .hasMessageContaining("Location filtering");
  }
}
