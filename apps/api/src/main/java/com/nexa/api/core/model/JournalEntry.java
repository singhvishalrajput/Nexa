package com.nexa.api.core.model;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne
  @JoinColumn(name = "TRANSACTION_ID")
  private BankTransaction transaction;

  private String entryReference;

  @Enumerated(EnumType.STRING)
  private JournalEntryType entryType;

  @Enumerated(EnumType.STRING)
  private JournalEntryStatus status;

  private LocalDateTime createdAt;

  @PrePersist
  void created() {
    createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public BankTransaction getTransaction() {
    return transaction;
  }

  public void setTransaction(BankTransaction v) {
    transaction = v;
  }

  public String getEntryReference() {
    return entryReference;
  }

  public void setEntryReference(String v) {
    entryReference = v;
  }

  public JournalEntryType getEntryType() {
    return entryType;
  }

  public void setEntryType(JournalEntryType v) {
    entryType = v;
  }

  public JournalEntryStatus getStatus() {
    return status;
  }

  public void setStatus(JournalEntryStatus v) {
    status = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
