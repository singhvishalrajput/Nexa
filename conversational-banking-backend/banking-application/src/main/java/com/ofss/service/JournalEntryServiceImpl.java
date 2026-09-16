package com.ofss.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ofss.beans.JournalEntry;
import com.ofss.exep.ResourceNotFoundException;
import com.ofss.repository.JournalEntryDao;

@Service
public class JournalEntryServiceImpl implements JournalEntryService {
	@Autowired
	JournalEntryDao dao;

	public List<JournalEntry> getAll() {
		return dao.findAll();
	}

	public JournalEntry getById(Long id) {
		return dao.findById(id).orElseThrow(() -> new ResourceNotFoundException("Journal entry not found: " + id));
	}

	public JournalEntry byTransaction(String id) {
		return dao.findByTransactionId(id)
				.orElseThrow(() -> new ResourceNotFoundException("Journal entry not found for transaction: " + id));
	}
}
