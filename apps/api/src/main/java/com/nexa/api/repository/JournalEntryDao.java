package com.nexa.api.repository;
import com.nexa.api.beans.JournalEntry;


import com.nexa.api.beans.JournalEntry;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryDao extends JpaRepository<JournalEntry, Long> {
  Optional<JournalEntry> findByTransactionId(String transactionId);
}
