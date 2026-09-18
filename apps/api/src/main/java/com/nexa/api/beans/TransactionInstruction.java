package com.nexa.api.beans;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Typed, non-posting transaction instructions and lifecycle events. Never balance movements. */
@Entity
@Table(name = "transactions")
@org.hibernate.annotations.SQLRestriction("record_kind <> 'PAYMENT'")
public class TransactionInstruction {
  @Id
  @Column(length = 40)
  private String id;

  @Column(nullable = false)
  @org.hibernate.annotations.ColumnDefault("'PAYMENT'")
  private String recordKind;

  @Column(length = 32)
  private String status;

  private String userId;

  @Column(name = "SOURCE_ACCOUNT_ID")
  private Long sourceAccountId;

  @Column(name = "DESTINATION_ACCOUNT_ID")
  private Long destinationAccountId;

  @Column(precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(precision = 19, scale = 2)
  private BigDecimal minimumAmount;

  private String currencyCode;
  private String parentId;
  private String operation;
  private Integer installmentNumber;
  @Column(precision = 19, scale = 2)
  private BigDecimal principalComponent;
  @Column(precision = 19, scale = 2)
  private BigDecimal interestComponent;
  private String auditReason;
  private String beforeName;
  private String afterName;
  private String beforeStatus;
  private String afterStatus;
  private String targetId;
  private String transactionReference;
  private String displayName;
  private String sourceName;
  private String sourceMasked;
  private String recipientName;
  private String destinationMasked;
  private String bankName;
  private String destinationHash;
  private String routingCode;
  private String beneficiaryType;
  private String frequency;
  private String effectiveDate;
  private String endDate;
  private String dueAt;
  private LocalDateTime expiresAt;

  @org.hibernate.annotations.ColumnDefault("CURRENT_TIMESTAMP")
  private LocalDateTime createdAt;

  private LocalDateTime completedAt;
  private LocalDateTime updatedAt;
}
