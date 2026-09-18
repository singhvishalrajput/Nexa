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
}
