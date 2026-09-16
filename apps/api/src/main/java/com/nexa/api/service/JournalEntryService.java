package com.nexa.api.service;
import com.nexa.api.beans.JournalEntry;


import com.nexa.api.beans.JournalEntry;
import java.util.*;

public interface JournalEntryService {
  List<JournalEntry> getAll();

  JournalEntry getById(Long id);

  JournalEntry byTransaction(String transactionId);
}
