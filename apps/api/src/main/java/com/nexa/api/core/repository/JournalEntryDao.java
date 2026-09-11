package com.nexa.api.core.repository;

import com.nexa.api.core.model.JournalEntry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryDao extends JpaRepository<JournalEntry, Long> {
  Optional<JournalEntry> findByTransactionId(String transactionId);
}
