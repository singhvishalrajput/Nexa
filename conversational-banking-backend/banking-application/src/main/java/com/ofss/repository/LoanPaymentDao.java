package com.ofss.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ofss.beans.LoanPayment;

public interface LoanPaymentDao extends JpaRepository<LoanPayment, Long> {
    List<LoanPayment> findByLoanIdOrderByPaidAtDesc(Long loanId);
    Optional<LoanPayment> findByInstallmentId(Long installmentId);
}
