package com.ofss.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.ofss.beans.*;
import com.ofss.exep.BadRequestException;

class LoanCalculationServiceTest {
    private final LoanCalculationServiceImpl service = new LoanCalculationServiceImpl();

    @BeforeEach
    void setRate() {
        ReflectionTestUtils.setField(service, "annualInterestRate", new BigDecimal("14.50"));
    }

    @Test
    void quoteUsesReducingBalanceAndAdjustsFinalInstallmentForPaise() {
        LoanQuoteResponse quote = service.quote(request("200000", 24));

        assertAll(
                () -> assertEquals("INR", quote.getCurrencyCode()),
                () -> assertEquals(new BigDecimal("14.50"), quote.getAnnualInterestRate()),
                () -> assertEquals(new BigDecimal("9649.89"), quote.getEmiAmount()),
                () -> assertEquals(new BigDecimal("9649.78"), quote.getFinalEmiAmount()),
                () -> assertEquals(new BigDecimal("31597.25"), quote.getTotalInterest()),
                () -> assertEquals(new BigDecimal("231597.25"), quote.getTotalRepayment()));
    }

    @Test
    void scheduleRepaysExactlyThePrincipalAndMatchesTheQuote() {
        Loan loan = loan("200000.00", "14.50", 24);
        List<LoanInstallment> schedule = service.schedule(loan, LocalDate.of(2026, 9, 16));
        LoanQuoteResponse quote = service.quote(request("200000.00", 24));

        assertEquals(24, schedule.size());
        assertEquals(new BigDecimal("200000.00"), sumPrincipal(schedule));
        assertEquals(quote.getTotalRepayment(), schedule.stream().map(LoanInstallment::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("7233.22"), schedule.get(0).getPrincipalAmount());
        assertEquals(new BigDecimal("2416.67"), schedule.get(0).getInterestAmount());
        for (int i = 0; i < schedule.size(); i++) {
            LoanInstallment installment = schedule.get(i);
            assertSame(loan, installment.getLoan());
            assertEquals(i + 1, installment.getInstallmentNumber());
            assertEquals(LoanInstallmentStatus.PENDING, installment.getStatus());
            assertEquals(installment.getPrincipalAmount().add(installment.getInterestAmount()), installment.getTotalAmount());
            assertTrue(installment.getPrincipalAmount().signum() > 0);
            assertEquals(2, installment.getTotalAmount().scale());
            if (i < schedule.size() - 1)
                assertEquals(quote.getEmiAmount(), installment.getTotalAmount());
        }
        assertEquals(quote.getFinalEmiAmount(), schedule.get(schedule.size() - 1).getTotalAmount());
    }

    @Test
    void zeroInterestHasNoDivisionByZeroAndSettlesTheRoundingRemainder() {
        ReflectionTestUtils.setField(service, "annualInterestRate", BigDecimal.ZERO);
        LoanQuoteResponse quote = service.quote(request("1000", 3));
        List<LoanInstallment> schedule = service.schedule(loan("1000.00", "0.00", 3), LocalDate.of(2026, 1, 1));

        assertEquals(new BigDecimal("333.33"), quote.getEmiAmount());
        assertEquals(new BigDecimal("333.34"), quote.getFinalEmiAmount());
        assertEquals(new BigDecimal("0.00"), quote.getTotalInterest());
        assertEquals(new BigDecimal("1000.00"), sumPrincipal(schedule));
        assertEquals(new BigDecimal("1000.00"), quote.getTotalRepayment());
    }

    @Test
    void dueDatesKeepTheDisbursementDayAfterFebruaryAndLeapYears() {
        List<LoanInstallment> ordinary = service.schedule(loan("1000", "14.50", 3), LocalDate.of(2026, 1, 31));
        assertEquals(List.of(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
                ordinary.stream().map(LoanInstallment::getDueDate).toList());

        List<LoanInstallment> leap = service.schedule(loan("1000", "14.50", 2), LocalDate.of(2028, 1, 31));
        assertEquals(LocalDate.of(2028, 2, 29), leap.get(0).getDueDate());
        assertEquals(LocalDate.of(2028, 3, 31), leap.get(1).getDueDate());
    }

    @ParameterizedTest
    @MethodSource("validBoundaries")
    void acceptsSupportedBoundariesWithoutLosingPrincipal(String amount, int months) {
        LoanQuoteResponse quote = service.quote(request(amount, months));
        List<LoanInstallment> schedule = service.schedule(loan(amount, "14.50", months), LocalDate.of(2026, 9, 16));
        assertEquals(new BigDecimal(amount).setScale(2), sumPrincipal(schedule));
        assertEquals(months, schedule.size());
        assertTrue(quote.getTotalRepayment().compareTo(new BigDecimal(amount)) > 0);
    }

    static Stream<Arguments> validBoundaries() {
        return Stream.of(Arguments.of("1000", 1), Arguments.of("1000000", 60),
                Arguments.of("1000.01", 60), Arguments.of("1000.0000", 1));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidAmountsAndTenures(String amount, Integer months) {
        assertThrows(BadRequestException.class, () -> service.quote(request(amount, months)));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(Arguments.of(null, 12), Arguments.of("-1000", 12), Arguments.of("0", 12),
                Arguments.of("999.99", 12), Arguments.of("1000000.01", 12), Arguments.of("1000.001", 12),
                Arguments.of("1000", null), Arguments.of("1000", -1), Arguments.of("1000", 0),
                Arguments.of("1000", 61));
    }

    @Test
    void rejectsMissingRequest() {
        assertThrows(BadRequestException.class, () -> service.quote(null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "-0.01", "50.01", "14.501" })
    void invalidConfiguredRatesCannotGenerateAnOffer(String rate) {
        ReflectionTestUtils.setField(service, "annualInterestRate", new BigDecimal(rate));
        assertThrows(IllegalStateException.class, () -> service.quote(request("1000", 12)));
    }

    private static BigDecimal sumPrincipal(List<LoanInstallment> schedule) {
        return schedule.stream().map(LoanInstallment::getPrincipalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LoanQuoteRequest request(String amount, Integer months) {
        LoanQuoteRequest request = new LoanQuoteRequest();
        request.setAmount(amount == null ? null : new BigDecimal(amount));
        request.setTenureMonths(months);
        return request;
    }

    private static Loan loan(String amount, String rate, int months) {
        Loan loan = new Loan();
        loan.setAmount(new BigDecimal(amount));
        loan.setInterestRate(new BigDecimal(rate));
        loan.setTenureMonths(months);
        return loan;
    }
}
