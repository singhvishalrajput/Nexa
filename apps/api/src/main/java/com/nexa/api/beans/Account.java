package com.nexa.api.beans;

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

  public Long getVersion() {
    return version;
  }

  @Column(length = 3)
  private String currencyCode = "INR";

  public String getCurrencyCode() {
    return currencyCode;
  }

  public void setCurrencyCode(String value) {
    currencyCode = value;
  }

  @Column(unique = true)
  private String productId;

  private Long fundingAccountId;

  @Column(precision = 19, scale = 2)
  private BigDecimal principalAmount;

  @Column(precision = 9, scale = 6)
  private BigDecimal interestRate;

  private String productStatus;
  private String productType;
  private String numberMasked;
  private String dueAt;

  @Column(length = 80)
  private String applicationKey;
  @Column(length = 200)
  private String loanPurpose;
  private Integer tenureMonths;
  private LocalDateTime approvedAt;
  private LocalDateTime closedAt;
  private String reviewedBy;
  @Column(length = 500)
  private String reviewReason;
  private LocalDateTime reviewedAt;

  @Column(precision = 19, scale = 2)
  private BigDecimal periodicPayment;

  @Column(precision = 19, scale = 2)
  private BigDecimal creditLimit;

  @Column(precision = 19, scale = 2)
  private BigDecimal minimumPayment;

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
