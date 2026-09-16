package com.nexa.api.beans;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class BankTransaction {
  @Id private String id;
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

  @Enumerated(EnumType.STRING)
  private TransactionType transactionType;

  @ManyToOne
  @JoinColumn(name = "SOURCE_ACCOUNT_ID")
  private Account sourceAccount;

  @ManyToOne
  @JoinColumn(name = "DESTINATION_ACCOUNT_ID")
  private Account destinationAccount;

  @Column(precision = 19, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  private TransactionStatus status;

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
    return transactionType;
  }

  public void setTransactionType(TransactionType v) {
    transactionType = v;
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
    return status;
  }

  public void setStatus(TransactionStatus v) {
    status = v;
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
