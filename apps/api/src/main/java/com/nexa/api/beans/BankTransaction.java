package com.nexa.api.beans;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@org.hibernate.annotations.SQLRestriction("record_kind = 'PAYMENT'")
public class BankTransaction {
  @Id private String id;

  @Column(nullable = false)
  @org.hibernate.annotations.ColumnDefault("'PAYMENT'")
  private String recordKind = "PAYMENT";

  private String parentId;
  private String operation;

  public void setParentId(String value) {
    parentId = value;
  }

  public void setOperation(String value) {
    operation = value;
  }

  private String merchantName;
  private String category;
  private String paymentMethod;

  public String getMerchantName() {
    return merchantName;
  }

  public String getCategory() {
    return category;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  private String transactionType;

  @ManyToOne
  @JoinColumn(name = "SOURCE_ACCOUNT_ID")
  private Account sourceAccount;

  @ManyToOne
  @JoinColumn(name = "DESTINATION_ACCOUNT_ID")
  private Account destinationAccount;

  @Column(precision = 19, scale = 2)
  private BigDecimal amount;

  private String status;

  private LocalDateTime createdAt;
  private LocalDateTime completedAt;

  @PrePersist
  void created() {
    if (id == null) {
      id = "TX-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
    createdAt = LocalDateTime.now();
  }

  public String getId() {
    return id;
  }

  public TransactionType getTransactionType() {
    return TransactionType.valueOf(transactionType);
  }

  public void setTransactionType(TransactionType v) {
    transactionType = v.name();
  }

  public Account getSourceAccount() {
    return sourceAccount;
  }

  public void setSourceAccount(Account v) {
    sourceAccount = v;
  }

  public Account getDestinationAccount() {
    return destinationAccount;
  }

  public void setDestinationAccount(Account v) {
    destinationAccount = v;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal v) {
    amount = v;
  }

  public TransactionStatus getStatus() {
    return TransactionStatus.valueOf(status);
  }

  public void setStatus(TransactionStatus v) {
    status = v.name();
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(LocalDateTime v) {
    completedAt = v;
  }
}
