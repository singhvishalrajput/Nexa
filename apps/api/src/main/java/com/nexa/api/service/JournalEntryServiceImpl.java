package com.nexa.api.service;
import com.nexa.api.beans.JournalEntry;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.JournalEntryDao;


import com.nexa.api.beans.JournalEntry;
import com.nexa.api.repository.JournalEntryDao;
import com.nexa.api.exep.ResourceNotFoundException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JournalEntryServiceImpl implements JournalEntryService {
  @Autowired JournalEntryDao dao;

  public List<JournalEntry> getAll() {
    return dao.findAll();
  }

  public JournalEntry getById(Long id) {
    return dao.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found: " + id));
  }

  public JournalEntry byTransaction(String id) {
    return dao.findByTransactionId(id)
        .orElseThrow(
            () -> new ResourceNotFoundException("Journal entry not found for transaction: " + id));
  }
}
