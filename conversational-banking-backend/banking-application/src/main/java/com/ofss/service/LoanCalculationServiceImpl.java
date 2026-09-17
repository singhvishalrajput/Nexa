package com.ofss.service;

import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ofss.beans.*;
import com.ofss.exep.BadRequestException;

@Service
public class LoanCalculationServiceImpl implements LoanCalculationService {
    private static final MathContext PRECISION = MathContext.DECIMAL128;

    @Value("${app.loans.annual-interest-rate:14.50}")
    BigDecimal annualInterestRate;

    @Override
    public LoanQuoteResponse quote(LoanQuoteRequest request) {
        if (request == null)
            throw new BadRequestException("Loan quote details are required");
        validate(request.getAmount(), request.getTenureMonths());
        if (annualInterestRate == null || annualInterestRate.signum() < 0
                || annualInterestRate.compareTo(new BigDecimal("50")) > 0
                || annualInterestRate.stripTrailingZeros().scale() > 2)
            throw new IllegalStateException("app.loans.annual-interest-rate must be 0 to 50 with at most two decimals");
        List<BigDecimal[]> amounts = calculate(request.getAmount(), annualInterestRate, request.getTenureMonths());
        BigDecimal interest = amounts.stream().map(row -> row[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        LoanQuoteResponse response = new LoanQuoteResponse();
        response.setAmount(request.getAmount().setScale(2));
        response.setCurrencyCode("INR");
        response.setAnnualInterestRate(annualInterestRate.setScale(2));
        response.setTenureMonths(request.getTenureMonths());
        response.setEmiAmount(amounts.get(0)[0].add(amounts.get(0)[1]));
        BigDecimal[] last = amounts.get(amounts.size() - 1);
        response.setFinalEmiAmount(last[0].add(last[1]));
        response.setTotalInterest(interest);
        response.setTotalRepayment(request.getAmount().add(interest).setScale(2));
        return response;
    }

    @Override
    public List<LoanInstallment> schedule(Loan loan, LocalDate disbursementDate) {
        List<BigDecimal[]> amounts = calculate(loan.getAmount(), loan.getInterestRate(), loan.getTenureMonths());
        List<LoanInstallment> result = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) {
            BigDecimal[] row = amounts.get(i);
            LoanInstallment installment = new LoanInstallment();
            installment.setLoan(loan);
            installment.setInstallmentNumber(i + 1);
            installment.setDueDate(disbursementDate.plusMonths(i + 1));
            installment.setPrincipalAmount(row[0]);
            installment.setInterestAmount(row[1]);
            installment.setTotalAmount(row[0].add(row[1]));
            installment.setStatus(LoanInstallmentStatus.PENDING);
            result.add(installment);
        }
        return result;
    }

    private List<BigDecimal[]> calculate(BigDecimal principal, BigDecimal rate, int months) {
        BigDecimal monthlyRate = rate.divide(new BigDecimal("1200"), PRECISION);
        BigDecimal emi;
        if (monthlyRate.signum() == 0) {
            emi = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(months, PRECISION);
            emi = principal.multiply(monthlyRate, PRECISION).multiply(factor, PRECISION)
                    .divide(factor.subtract(BigDecimal.ONE), PRECISION).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal remaining = principal.setScale(2);
        List<BigDecimal[]> result = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            BigDecimal interest = remaining.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal capital = i == months ? remaining : emi.subtract(interest).min(remaining);
            result.add(new BigDecimal[] { capital, interest });
            remaining = remaining.subtract(capital);
        }
        return result;
    }

    private void validate(BigDecimal amount, Integer months) {
        if (amount == null || amount.compareTo(new BigDecimal("1000")) < 0
                || amount.compareTo(new BigDecimal("1000000")) > 0 || amount.stripTrailingZeros().scale() > 2
                || months == null || months < 1 || months > 60)
            throw new BadRequestException("Loans support INR 1,000 to 10,00,000 with at most two decimals and 1 to 60 months");
    }
}
