package com.nexa.api.core.service;

import com.nexa.api.core.model.LedgerEntry;
import com.nexa.api.core.repository.LedgerEntryDao;
import com.nexa.api.shared.errors.ResourceNotFoundException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LedgerEntryServiceImpl implements LedgerEntryService {
  @Autowired LedgerEntryDao dao;

  public List<LedgerEntry> getAll() {
    return dao.findAll();
  }

  public LedgerEntry getById(Long id) {
    return dao.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Ledger entry not found: " + id));
  }

  public List<LedgerEntry> byAccount(Long id) {
    return dao.findByAccountId(id);
  }

  public List<LedgerEntry> byJournalEntry(Long id) {
    return dao.findByJournalEntryId(id);
  }
}
