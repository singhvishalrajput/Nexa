package com.nexa.api.core.service;

import com.nexa.api.core.model.JournalEntry;
import java.util.*;

public interface JournalEntryService {
  List<JournalEntry> getAll();

  JournalEntry getById(Long id);

  JournalEntry byTransaction(String transactionId);
}
