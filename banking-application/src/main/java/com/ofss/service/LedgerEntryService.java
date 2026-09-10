package com.ofss.service;

import java.util.*;
import com.ofss.beans.LedgerEntry;

public interface LedgerEntryService {
	List<LedgerEntry> getAll();

	LedgerEntry getById(Long id);

	List<LedgerEntry> byAccount(Long accountId);

	List<LedgerEntry> byJournalEntry(Long journalEntryId);
}
