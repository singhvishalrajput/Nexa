package com.ofss.service;

import java.util.*;
import com.ofss.beans.JournalEntry;

public interface JournalEntryService {
	List<JournalEntry> getAll();

	JournalEntry getById(Long id);

	JournalEntry byTransaction(String transactionId);
}
