package com.nexa.api.beans;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Loan contracts use the same public product and transaction IDs as the banking APIs. */
public final class LoanModels {
  private LoanModels() {}

  public record QuoteRequest(BigDecimal amount, Integer tenureMonths) {}

  public record Quote(
      BigDecimal amount,
      String currencyCode,
      BigDecimal annualInterestRate,
      int tenureMonths,
      BigDecimal emiAmount,
      BigDecimal finalEmiAmount,
      BigDecimal totalInterest,
      BigDecimal totalRepayment) {}

  public record Terms(
      String applicationKey,
      String purpose,
      BigDecimal amount,
      BigDecimal annualInterestRate,
      Integer tenureMonths,
      BigDecimal emiAmount,
      LocalDateTime approvedAt,
      LocalDateTime closedAt) {}

  public record Installment(
      String id,
      String loanId,
      int installmentNumber,
      LocalDate dueDate,
      BigDecimal principalAmount,
      BigDecimal interestAmount,
      BigDecimal totalAmount,
      String status,
      LocalDateTime paidAt) {}

  public record Payment(
      String id,
      String loanId,
      String installmentId,
      long accountId,
      String reference,
      String type,
      String currencyCode,
      BigDecimal amount,
      BigDecimal principalAmount,
      BigDecimal interestAmount,
      String status,
      LocalDateTime paidAt) {}
}
