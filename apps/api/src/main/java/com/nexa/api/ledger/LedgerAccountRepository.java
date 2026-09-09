package com.nexa.api.ledger;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface LedgerAccountRepository extends JpaRepository<LedgerAccountEntity, String> {
    Optional<LedgerAccountEntity> findByAccountCode(String accountCode);
}
