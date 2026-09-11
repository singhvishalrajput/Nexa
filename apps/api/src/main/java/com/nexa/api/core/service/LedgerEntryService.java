package com.nexa.api.core.service;

import com.nexa.api.core.model.LedgerEntry;
import java.util.*;

public interface LedgerEntryService {
  List<LedgerEntry> getAll();

  LedgerEntry getById(Long id);

  List<LedgerEntry> byAccount(Long accountId);

  List<LedgerEntry> byJournalEntry(Long journalEntryId);
}
