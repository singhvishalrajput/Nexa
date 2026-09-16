package com.nexa.api.repository;
import com.nexa.api.beans.LedgerEntry;


import com.nexa.api.beans.LedgerEntry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryDao extends JpaRepository<LedgerEntry, Long> {
  List<LedgerEntry> findByAccountId(Long accountId);

  List<LedgerEntry> findByJournalEntryId(Long journalEntryId);
}
