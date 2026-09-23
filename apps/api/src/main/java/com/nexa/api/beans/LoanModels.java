package com.nexa.api.beans;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Loan contracts use the same public product and transaction IDs as the banking APIs. */
public final class LoanModels {
  private LoanModels() {}

  public record QuoteRequest(BigDecimal amount, Integer tenureMonths) {}

  public enum PrepaymentOption { REDUCE_TENURE, REDUCE_EMI }

  public record RepaymentPreviewRequest(BigDecimal amount) {}

  public record PrepaymentResult(
      PrepaymentOption option, boolean available, String unavailableReason,
      BigDecimal regularEmi, BigDecimal finalEmi, int remainingInstallments,
      LocalDate finalDueDate, BigDecimal futureInterest, BigDecimal futureRepayment,
      BigDecimal interestSaved, int installmentsSaved, java.util.List<Installment> schedule) {}

  public record RepaymentPreview(
      String previewToken, BigDecimal paymentAmount, BigDecimal interestAmount,
      BigDecimal principalAmount, BigDecimal extraPrincipalAmount, BigDecimal remainingPrincipal,
      BigDecimal annualInterestRate, BigDecimal currentEmi, int baselineInstallments,
      LocalDate baselineFinalDueDate, BigDecimal baselineFutureInterest, boolean closesLoan,
      java.util.List<PrepaymentResult> options) {}

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

  public record RepaymentOptions(
      BigDecimal minimumAmount,
      BigDecimal maximumAmount,
      BigDecimal interestAmount,
      boolean principalOnly,
      BigDecimal regularEmi,
      int remainingInstallments,
      LocalDate finalDueDate,
      BigDecimal minimumExtraPrincipal) {}
}
