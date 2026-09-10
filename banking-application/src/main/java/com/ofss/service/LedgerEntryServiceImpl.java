package com.ofss.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ofss.beans.LedgerEntry;
import com.ofss.exep.ResourceNotFoundException;
import com.ofss.repository.LedgerEntryDao;

@Service
public class LedgerEntryServiceImpl implements LedgerEntryService {
	@Autowired
	LedgerEntryDao dao;

	public List<LedgerEntry> getAll() {
		return dao.findAll();
	}

	public LedgerEntry getById(Long id) {
		return dao.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ledger entry not found: " + id));
	}

	public List<LedgerEntry> byAccount(Long id) {
		return dao.findByAccountId(id);
	}

	public List<LedgerEntry> byJournalEntry(Long id) {
		return dao.findByJournalEntryId(id);
	}
}
