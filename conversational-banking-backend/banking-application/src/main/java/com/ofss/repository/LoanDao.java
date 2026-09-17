package com.ofss.repository;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import com.ofss.beans.Loan;

public interface LoanDao extends JpaRepository<Loan, Long> {
    List<Loan> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Optional<Loan> findByIdAndCustomerId(Long id, Long customerId);
    Optional<Loan> findByCustomerIdAndApplicationKey(Long customerId, String applicationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id and l.customer.id = :customerId")
    Optional<Loan> findOwnedForUpdate(Long id, Long customerId);
}
