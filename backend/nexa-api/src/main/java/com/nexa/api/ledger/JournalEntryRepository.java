package com.nexa.api.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, String> {
}
