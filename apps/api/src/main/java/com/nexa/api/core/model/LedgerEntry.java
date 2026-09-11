package com.nexa.api.core.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "JOURNAL_ENTRY_ID")
  private JournalEntry journalEntry;

  @ManyToOne
  @JoinColumn(name = "ACCOUNT_ID")
  private Account account;

  @Enumerated(EnumType.STRING)
  private LedgerEntryType entryType;

  @Column(precision = 19, scale = 2)
  private BigDecimal amount;

  private LocalDateTime createdAt;

  @PrePersist
  void created() {
    createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public JournalEntry getJournalEntry() {
    return journalEntry;
  }

  public void setJournalEntry(JournalEntry v) {
    journalEntry = v;
  }

  public Account getAccount() {
    return account;
  }

  public void setAccount(Account v) {
    account = v;
  }

  public LedgerEntryType getEntryType() {
    return entryType;
  }

  public void setEntryType(LedgerEntryType v) {
    entryType = v;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal v) {
    amount = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
