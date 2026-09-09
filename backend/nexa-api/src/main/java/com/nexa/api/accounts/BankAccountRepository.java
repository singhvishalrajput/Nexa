package com.nexa.api.accounts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface BankAccountRepository extends JpaRepository<BankAccountEntity, String> {

    List<BankAccountEntity> findByUserIdOrderByCreatedAtAsc(String userId);

    Optional<BankAccountEntity> findByIdAndUserId(String id, String userId);

    boolean existsByUserId(String userId);

    boolean existsByAccountNumber(String accountNumber);
}
