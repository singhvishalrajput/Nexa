package com.ofss.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ofss.beans.*;

public interface LoanInstallmentDao extends JpaRepository<LoanInstallment, Long> {
    List<LoanInstallment> findByLoanIdOrderByInstallmentNumberAsc(Long loanId);
    Optional<LoanInstallment> findByIdAndLoanId(Long id, Long loanId);
    Optional<LoanInstallment> findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(Long loanId, LoanInstallmentStatus status);
}
