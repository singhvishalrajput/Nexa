package com.nexa.api.service;
import com.nexa.api.beans.LedgerEntry;


import com.nexa.api.beans.LedgerEntry;
import java.util.*;

public interface LedgerEntryService {
  List<LedgerEntry> getAll();

  LedgerEntry getById(Long id);

  List<LedgerEntry> byAccount(Long accountId);

  List<LedgerEntry> byJournalEntry(Long journalEntryId);
}
