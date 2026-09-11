package com.nexa.api.core.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "accounts")
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version @com.fasterxml.jackson.annotation.JsonIgnore private Long version;

  @Column(length = 3)
  private String currencyCode = "INR";

  public String getCurrencyCode() {
    return currencyCode;
  }

  public void setCurrencyCode(String value) {
    currencyCode = value;
  }

  private String accountNumber;

  @ManyToOne
  @JoinColumn(name = "CUSTOMER_ID")
  private Customer customer;

  private String accountName;

  @Enumerated(EnumType.STRING)
  private AccountType accountType;

  @Enumerated(EnumType.STRING)
  private AccountCategory accountCategory;

  @Column(precision = 19, scale = 2)
  private BigDecimal balance = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  private AccountStatus status = AccountStatus.ACTIVE;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @PrePersist
  void created() {
    createdAt = updatedAt = LocalDateTime.now();
    if (balance == null) balance = BigDecimal.ZERO;
    if (status == null) status = AccountStatus.ACTIVE;
  }

  @PreUpdate
  void updated() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String v) {
    accountNumber = v;
  }

  public Customer getCustomer() {
    return customer;
  }

  public void setCustomer(Customer v) {
    customer = v;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String v) {
    accountName = v;
  }

  public AccountType getAccountType() {
    return accountType;
  }

  public void setAccountType(AccountType v) {
    accountType = v;
  }

  public AccountCategory getAccountCategory() {
    return accountCategory;
  }

  public void setAccountCategory(AccountCategory v) {
    accountCategory = v;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal v) {
    balance = v;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public void setStatus(AccountStatus v) {
    status = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
