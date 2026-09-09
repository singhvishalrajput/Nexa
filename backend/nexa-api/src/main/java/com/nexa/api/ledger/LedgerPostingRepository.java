package com.nexa.api.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

interface LedgerPostingRepository extends JpaRepository<LedgerPostingEntity, String> {
}
