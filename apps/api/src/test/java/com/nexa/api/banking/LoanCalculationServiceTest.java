package com.nexa.api.banking;

import static org.assertj.core.api.Assertions.*;

import com.nexa.api.beans.LoanModels.*;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.service.LoanCalculationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanCalculationServiceTest {
  @Test
  void reducedEmiKeepsDatesAndPrincipalAndBothOptionsUseSameFixedRate() {
    for (String rateText : List.of("0", "14.50", "50")) {
      BigDecimal rate = new BigDecimal(rateText);
      var original = service.schedule(new BigDecimal("100000"), rate, 24, LocalDate.of(2028, 1, 31));
      var unpaid = original.subList(1, original.size());
      var smaller = service.reduceEmi(new BigDecimal("50000"), rate, unpaid);
      var shorter = service.recalculate(new BigDecimal("50000"), rate, original.get(0).totalAmount(), unpaid);
      assertThat(smaller).extracting(Installment::dueDate)
          .containsExactlyElementsOf(unpaid.stream().map(Installment::dueDate).toList());
      assertThat(smaller.get(0).totalAmount()).isLessThan(original.get(0).totalAmount());
      assertThat(shorter).hasSizeLessThan(smaller.size());
      for (var rows : List.of(smaller, shorter))
        assertThat(rows.stream().map(Installment::principalAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("50000");
      assertThat(shorter.stream().map(Installment::interestAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
          .isLessThanOrEqualTo(smaller.stream().map(Installment::interestAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
  }

  @Test
  void reducedEmiHandlesFullPayoffAndUnrepresentableTinyInstallments() {
    var original = service.schedule(new BigDecimal("12000"), new BigDecimal("14.50"), 12, LocalDate.of(2026, 1, 31));
    assertThat(service.reduceEmi(BigDecimal.ZERO, new BigDecimal("14.50"), original)).isEmpty();
    assertThatThrownBy(() -> service.reduceEmi(new BigDecimal("0.01"), BigDecimal.ZERO, original))
        .isInstanceOf(InvalidRequestException.class);
    var one = service.reduceEmi(new BigDecimal("100.01"), BigDecimal.ZERO, original.subList(0, 1));
    assertThat(one.get(0).totalAmount()).isEqualByComparingTo("100.01");
  }
  private final LoanCalculationService service =
      new LoanCalculationService(new BigDecimal("14.50"));

  @Test
  void referenceQuoteAndSchedulePreserveEveryPaise() {
    var quote = service.quote(new QuoteRequest(new BigDecimal("200000"), 24));
    assertThat(quote.emiAmount()).isEqualByComparingTo("9649.89");
    assertThat(quote.finalEmiAmount()).isEqualByComparingTo("9649.78");
    assertThat(quote.totalInterest()).isEqualByComparingTo("31597.25");
    assertThat(quote.totalRepayment()).isEqualByComparingTo("231597.25");
    for (String amount : List.of("1000", "1000000", "1000.01", "1000.0000")) {
      for (int months : List.of(1, 3, 24, 60)) {
        var schedule =
            service.schedule(
                new BigDecimal(amount), new BigDecimal("14.50"), months, LocalDate.of(2026, 1, 31));
        assertThat(
                schedule.stream()
                    .map(Installment::principalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo(amount);
        assertThat(
                schedule.stream()
                    .map(Installment::totalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo(
                service.quote(new QuoteRequest(new BigDecimal(amount), months)).totalRepayment());
        assertThat(schedule).allSatisfy(row -> assertThat(row.principalAmount()).isPositive());
      }
    }
  }

  @Test
  void zeroInterestRoundingAndMonthEndsMatchReference() {
    var zero =
        new LoanCalculationService(BigDecimal.ZERO)
            .quote(new QuoteRequest(new BigDecimal("1000"), 3));
    assertThat(zero.emiAmount()).isEqualByComparingTo("333.33");
    assertThat(zero.finalEmiAmount()).isEqualByComparingTo("333.34");
    assertThat(zero.totalInterest()).isZero();
    for (int year : List.of(2026, 2028)) {
      var rows =
          service.schedule(new BigDecimal("1000"), BigDecimal.ZERO, 3, LocalDate.of(year, 1, 31));
      assertThat(rows)
          .extracting(Installment::dueDate)
          .containsExactly(
              LocalDate.of(year, 2, year == 2028 ? 29 : 28),
              LocalDate.of(year, 3, 31),
              LocalDate.of(year, 4, 30));
    }
  }

  @Test
  void invalidRequestsAndConfigurationAreRejected() {
    assertThatThrownBy(() -> service.quote(null)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.quote(new QuoteRequest(null, 3)))
        .isInstanceOf(InvalidRequestException.class);
    for (String amount : List.of("-1000", "0", "999.99", "1000000.01", "1000.001"))
      assertThatThrownBy(() -> service.quote(new QuoteRequest(new BigDecimal(amount), 3)))
          .isInstanceOf(InvalidRequestException.class);
    for (Integer months : java.util.Arrays.asList(null, -1, 0, 61))
      assertThatThrownBy(() -> service.quote(new QuoteRequest(new BigDecimal("1000"), months)))
          .isInstanceOf(InvalidRequestException.class);
    for (String rate : List.of("-0.01", "50.01", "14.501"))
      assertThatThrownBy(() -> new LoanCalculationService(new BigDecimal(rate)))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void principalPrepaymentKeepsEmiShortensTenureAndLowersInterest() {
    var original = service.schedule(new BigDecimal("12000"), new BigDecimal("14.50"),
        12, LocalDate.of(2026, 1, 31));
    BigDecimal emi = original.get(0).totalAmount();
    var revised = service.recalculate(new BigDecimal("6000"), new BigDecimal("14.50"),
        emi, original.subList(1, original.size()));
    assertThat(revised).hasSizeLessThan(original.size() - 1);
    assertThat(revised.get(0).interestAmount()).isEqualByComparingTo("72.50");
    assertThat(revised.get(0).dueDate()).isEqualTo(original.get(1).dueDate());
    assertThat(revised.subList(0, revised.size() - 1))
        .allSatisfy(row -> assertThat(row.totalAmount()).isEqualByComparingTo(emi));
    assertThat(revised.get(revised.size() - 1).totalAmount()).isLessThanOrEqualTo(emi);
    assertThat(revised.stream().map(Installment::principalAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("6000");
    assertThat(service.recalculate(BigDecimal.ZERO, new BigDecimal("14.50"), emi, original))
        .isEmpty();
    var zero = service.recalculate(new BigDecimal("100.01"), BigDecimal.ZERO,
        new BigDecimal("100"), original);
    assertThat(zero).hasSize(2);
    assertThat(zero.get(1).totalAmount()).isEqualByComparingTo("0.01");
  }
}
