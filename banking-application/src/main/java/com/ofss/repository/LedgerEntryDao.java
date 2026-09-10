package com.ofss.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ofss.beans.LedgerEntry;

public interface LedgerEntryDao extends JpaRepository<LedgerEntry, Long> {
	List<LedgerEntry> findByAccountId(Long accountId);

	List<LedgerEntry> findByJournalEntryId(Long journalEntryId);
}
