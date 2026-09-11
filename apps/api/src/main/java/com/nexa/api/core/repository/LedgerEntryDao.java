package com.nexa.api.core.repository;

import com.nexa.api.core.model.LedgerEntry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryDao extends JpaRepository<LedgerEntry, Long> {
  List<LedgerEntry> findByAccountId(Long accountId);

  List<LedgerEntry> findByJournalEntryId(Long journalEntryId);
}
