package com.ofss.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ofss.beans.JournalEntry;

public interface JournalEntryDao extends JpaRepository<JournalEntry, Long> {
	Optional<JournalEntry> findByTransactionId(String transactionId);
}
