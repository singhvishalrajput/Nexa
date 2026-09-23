package com.nexa.api.service;

import com.nexa.api.beans.LoanModels.*;
import com.nexa.api.exep.InvalidRequestException;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoanCalculationService {
  private static final MathContext PRECISION = MathContext.DECIMAL128;
  private final BigDecimal annualInterestRate;

  public LoanCalculationService(@Value("${app.loans.annual-interest-rate:14.50}") BigDecimal rate) {
    if (rate == null
        || rate.signum() < 0
        || rate.compareTo(new BigDecimal("50")) > 0
        || rate.stripTrailingZeros().scale() > 2)
      throw new IllegalArgumentException(
          "app.loans.annual-interest-rate must be 0 to 50 with at most two decimals");
    annualInterestRate = rate.setScale(2);
  }

  public Quote quote(QuoteRequest request) {
    if (request == null
        || request.amount() == null
        || request.amount().compareTo(new BigDecimal("1000")) < 0
        || request.amount().compareTo(new BigDecimal("1000000")) > 0
        || request.amount().stripTrailingZeros().scale() > 2
        || request.tenureMonths() == null
        || request.tenureMonths() < 1
        || request.tenureMonths() > 60)
      throw new InvalidRequestException(
          "Loans support INR 1,000 to 10,00,000 with at most two decimals and 1 to 60 months");
    var rows =
        schedule(
            request.amount(), annualInterestRate, request.tenureMonths(), LocalDate.of(2000, 1, 1));
    BigDecimal interest =
        rows.stream().map(Installment::interestAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new Quote(
        request.amount().setScale(2),
        "INR",
        annualInterestRate,
        request.tenureMonths(),
        rows.get(0).totalAmount(),
        rows.get(rows.size() - 1).totalAmount(),
        interest,
        request.amount().add(interest).setScale(2));
  }

  public List<Installment> schedule(
      BigDecimal principal, BigDecimal rate, int months, LocalDate date) {
    BigDecimal emi = emi(principal, rate, months);
    BigDecimal monthlyRate = rate.divide(new BigDecimal("1200"), PRECISION);
    BigDecimal remaining = principal.setScale(2);
    List<Installment> result = new ArrayList<>();
    for (int i = 1; i <= months; i++) {
      BigDecimal interest = remaining.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
      BigDecimal capital = i == months ? remaining : emi.subtract(interest).min(remaining);
      result.add(new Installment(null, null, i, date.plusMonths(i), capital, interest,
          capital.add(interest), "PENDING", null));
      remaining = remaining.subtract(capital);
    }
    return result;
  }

  public BigDecimal emi(BigDecimal principal, BigDecimal rate, int months) {
    if (months < 1 || months > 60 || principal.signum() <= 0 || rate.signum() < 0)
      throw new InvalidRequestException("A positive balance and 1 to 60 installments are required");
    BigDecimal monthlyRate = rate.divide(new BigDecimal("1200"), PRECISION);
    BigDecimal emi;
    if (monthlyRate.signum() == 0) {
      emi = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    } else {
      BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(months, PRECISION);
      emi =
          principal
              .multiply(monthlyRate, PRECISION)
              .multiply(factor, PRECISION)
              .divide(factor.subtract(BigDecimal.ONE), PRECISION)
              .setScale(2, RoundingMode.HALF_UP);
    }
    return emi;
  }

  /** Keep existing unpaid dates and identities, including the current projected maturity. */
  public List<Installment> reduceEmi(BigDecimal principal, BigDecimal rate, List<Installment> unpaid) {
    if (principal.signum() == 0) return List.of();
    var revised = recalculate(principal, rate, emi(principal, rate, unpaid.size()), unpaid);
    if (revised.size() != unpaid.size())
      throw new InvalidRequestException("Balance is too small to retain all due dates; choose reduce tenure");
    return revised;
  }

  /** Keep contractual EMI and existing due dates; only unpaid projections are replaced. */
  public List<Installment> recalculate(
      BigDecimal principal, BigDecimal rate, BigDecimal emi, List<Installment> unpaid) {
    BigDecimal remaining = principal.setScale(2);
    BigDecimal monthlyRate = rate.divide(new BigDecimal("1200"), PRECISION);
    List<Installment> result = new ArrayList<>();
    for (int i = 0; i < unpaid.size() && remaining.signum() > 0; i++) {
      var old = unpaid.get(i);
      BigDecimal interest = remaining.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
      BigDecimal capital =
          i == unpaid.size() - 1 ? remaining : emi.subtract(interest).min(remaining);
      if (capital.signum() <= 0)
        throw new InvalidRequestException("The EMI must cover interest and principal");
      result.add(
          new Installment(
              old.id(), old.loanId(), old.installmentNumber(), old.dueDate(),
              capital, interest, capital.add(interest), "PENDING", null));
      remaining = remaining.subtract(capital);
    }
    if (remaining.signum() != 0)
      throw new IllegalStateException("No remaining installments for outstanding principal");
    return result;
  }
}
