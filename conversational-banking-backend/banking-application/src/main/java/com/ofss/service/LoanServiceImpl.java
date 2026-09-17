package com.ofss.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import org.springframework.beans.factory.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.*;
import com.ofss.exep.*;
import com.ofss.repository.*;

@Service
public class LoanServiceImpl implements LoanService {
    @Autowired
    LoanDao loans;

    @Autowired
    LoanInstallmentDao installments;

    @Autowired
    LoanPaymentDao payments;

    @Autowired
    LoanCalculationService calculation;

    @Autowired
    AccountLockService accountLocks;

    @Autowired
    @Qualifier("loanClock")
    Clock clock;

    @Override
    @Transactional
    public LoanResponse apply(LoanApplicationRequest request, Long customerId) {
        validateApplication(request);
        LoanQuoteRequest quoteRequest = new LoanQuoteRequest();
        quoteRequest.setAmount(request.getAmount());
        quoteRequest.setTenureMonths(request.getTenureMonths());
        LoanQuoteResponse quote = calculation.quote(quoteRequest);
        Account account = requireAccount(request.getAccountId(), customerId);
        Loan existing = loans.findByCustomerIdAndApplicationKey(customerId, request.getApplicationKey()).orElse(null);
        if (existing != null) {
            if (!existing.getAccount().getId().equals(request.getAccountId())
                    || existing.getAmount().compareTo(request.getAmount()) != 0
                    || !existing.getTenureMonths().equals(request.getTenureMonths())
                    || !existing.getPurpose().equals(request.getPurpose().trim()))
                throw new BadRequestException("Application key was already used with different loan details");
            return response(existing);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Loan loan = new Loan();
        loan.setCustomer(account.getCustomer());
        loan.setAccount(account);
        loan.setApplicationKey(request.getApplicationKey());
        loan.setPurpose(request.getPurpose().trim());
        loan.setAmount(quote.getAmount());
        loan.setInterestRate(quote.getAnnualInterestRate());
        loan.setTenureMonths(quote.getTenureMonths());
        loan.setEmiAmount(quote.getEmiAmount());
        loan.setOutstandingAmount(BigDecimal.ZERO.setScale(2));
        loan.setStatus(LoanStatus.APPROVED);
        loan.setCreatedAt(now);
        loan.setApprovedAt(now);
        // Every valid submitted application is approved; there is no eligibility decision.
        try {
            loans.saveAndFlush(loan);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("Loan application could not be created; use the original application key and details when retrying");
        }
        return response(loan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanResponse> byCustomer(Long customerId) {
        return loans.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LoanResponse getById(Long id, Long customerId) {
        return response(requireOwned(id, customerId));
    }

    @Override
    @Transactional
    public LoanResponse accept(Long id, Long customerId) {
        Loan loan = requireOwnedForUpdate(id, customerId);
        if (loan.getStatus() == LoanStatus.ACTIVE || loan.getStatus() == LoanStatus.CLOSED)
            return response(loan);
        if (loan.getStatus() != LoanStatus.APPROVED)
            throw new BadRequestException("Only an approved loan can be accepted");

        Account account = requireAccount(loan.getAccount().getId(), customerId);
        LocalDateTime now = LocalDateTime.now(clock);
        List<LoanInstallment> schedule = calculation.schedule(loan, now.toLocalDate());
        account.setBalance(account.getBalance().add(loan.getAmount()));
        installments.saveAll(schedule);
        recordPayment(loan, null, account, "LOAN-DISBURSE-" + loan.getId(),
                LoanPaymentType.DISBURSEMENT, loan.getAmount(), BigDecimal.ZERO.setScale(2), now);
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setOutstandingAmount(loan.getAmount());
        loan.setNextDueDate(schedule.get(0).getDueDate());
        return response(loan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanInstallmentResponse> schedule(Long id, Long customerId) {
        requireOwned(id, customerId);
        return installments.findByLoanIdOrderByInstallmentNumberAsc(id).stream()
                .map(row -> LoanInstallmentResponse.from(row, LocalDate.now(clock))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanPaymentResponse> payments(Long id, Long customerId) {
        requireOwned(id, customerId);
        return payments.findByLoanIdOrderByPaidAtDesc(id).stream().map(LoanPaymentResponse::from).toList();
    }

    @Override
    @Transactional
    public LoanPaymentResponse payInstallment(Long id, Long installmentId, Long customerId) {
        Loan loan = requireOwnedForUpdate(id, customerId);
        LoanInstallment installment = installments.findByIdAndLoanId(installmentId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan installment not found"));
        if (installment.getStatus() == LoanInstallmentStatus.PAID)
            return LoanPaymentResponse.from(payments.findByInstallmentId(installmentId)
                    .orElseThrow(() -> new IllegalStateException("Installment payment record is missing")));
        if (loan.getStatus() != LoanStatus.ACTIVE)
            throw new BadRequestException("Only an active loan can be repaid");
        LoanInstallment next = installments.findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(id, LoanInstallmentStatus.PENDING)
                .orElseThrow(() -> new BadRequestException("There are no unpaid installments"));
        if (!next.getId().equals(installmentId))
            throw new BadRequestException("Pay the earliest unpaid installment first");

        Account account = requireAccount(loan.getAccount().getId(), customerId);
        if (account.getBalance().compareTo(installment.getTotalAmount()) < 0)
            throw new BadRequestException("Insufficient balance for this EMI");
        LocalDateTime now = LocalDateTime.now(clock);
        account.setBalance(account.getBalance().subtract(installment.getTotalAmount()));
        LoanPayment payment = recordPayment(loan, installment, account, "LOAN-EMI-" + installmentId,
                LoanPaymentType.EMI_PAYMENT, installment.getPrincipalAmount(), installment.getInterestAmount(), now);
        installment.setStatus(LoanInstallmentStatus.PAID);
        installment.setPaidAt(now);
        // The query flushes the paid installment before selecting the next one.
        LocalDate nextDueDate = installments.findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(id, LoanInstallmentStatus.PENDING)
                .map(LoanInstallment::getDueDate).orElse(null);
        loan.setOutstandingAmount(loan.getOutstandingAmount().subtract(installment.getPrincipalAmount()));
        loan.setNextDueDate(nextDueDate);
        if (loan.getOutstandingAmount().signum() == 0) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setClosedAt(now);
            loan.setNextDueDate(null);
        }
        return LoanPaymentResponse.from(payment);
    }

    private LoanPayment recordPayment(Loan loan, LoanInstallment installment, Account account,
            String reference, LoanPaymentType type, BigDecimal principal, BigDecimal interest, LocalDateTime now) {
        LoanPayment payment = new LoanPayment();
        payment.setLoan(loan);
        payment.setInstallment(installment);
        payment.setAccount(account);
        payment.setPaymentReference(reference);
        payment.setPaymentType(type);
        payment.setPrincipalAmount(principal);
        payment.setInterestAmount(interest);
        payment.setAmount(principal.add(interest));
        payment.setStatus(LoanPaymentStatus.POSTED);
        payment.setPaidAt(now);
        return payments.save(payment);
    }

    private Account requireAccount(Long id, Long customerId) {
        Account account = accountLocks.lockAccount(id);
        if (customerId == null || account.getCustomer() == null || !customerId.equals(account.getCustomer().getId())
                || account.getAccountCategory() != AccountCategory.CUSTOMER)
            throw new ResourceNotFoundException("Customer account not found");
        if (account.getStatus() != AccountStatus.ACTIVE
                || (account.getAccountType() != AccountType.SAVINGS && account.getAccountType() != AccountType.CURRENT))
            throw new BadRequestException("Loans require an ACTIVE savings or current account");
        return account;
    }

    private Loan requireOwned(Long id, Long customerId) {
        return loans.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
    }

    private Loan requireOwnedForUpdate(Long id, Long customerId) {
        return loans.findOwnedForUpdate(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
    }

    private LoanResponse response(Loan loan) {
        return LoanResponse.from(loan, LocalDate.now(clock));
    }

    private void validateApplication(LoanApplicationRequest request) {
        if (request == null || request.getAccountId() == null || request.getAccountId() <= 0)
            throw new BadRequestException("A valid accountId is required");
        if (request.getApplicationKey() == null || !request.getApplicationKey().matches("[A-Za-z0-9_-]{1,80}"))
            throw new BadRequestException("applicationKey requires 1 to 80 letters, digits, underscores or hyphens");
        if (request.getPurpose() == null || request.getPurpose().isBlank() || request.getPurpose().length() > 200)
            throw new BadRequestException("Purpose is required and must not exceed 200 characters");
    }
}
