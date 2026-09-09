package com.nexa.api.transactions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "TRANSACTIONS")
class TransactionEntity {

    @Id
    @Column(name = "ID", length = 26, nullable = false)
    private String id;

    @Column(name = "BANK_ACCOUNT_ID", length = 26, nullable = false)
    private String bankAccountId;

    @Column(name = "JOURNAL_ENTRY_ID", length = 26)
    private String journalEntryId;

    @Column(name = "TRANSACTION_REFERENCE", length = 80, nullable = false, unique = true)
    private String transactionReference;

    @Column(name = "TRANSACTION_TYPE", length = 32, nullable = false)
    private String transactionType;

    @Column(name = "MERCHANT_NAME", length = 200)
    private String merchantName;

    @Column(name = "CATEGORY", length = 80)
    private String category;

    @Column(name = "AMOUNT", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "CURRENCY_CODE", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "STATUS", length = 24, nullable = false)
    private String status;

    @Column(name = "OCCURRED_AT", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    protected TransactionEntity() {
    }

    TransactionEntity(
            String id,
            String bankAccountId,
            String journalEntryId,
            String transactionReference,
            String transactionType,
            String merchantName,
            String category,
            BigDecimal amount,
            String currencyCode,
            String status,
            OffsetDateTime now) {
        this.id = id;
        this.bankAccountId = bankAccountId;
        this.journalEntryId = journalEntryId;
        this.transactionReference = transactionReference;
        this.transactionType = transactionType;
        this.merchantName = merchantName;
        this.category = category;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.status = status;
        this.occurredAt = now;
        this.createdAt = now;
    }

    String getId() { return id; }
    String getBankAccountId() { return bankAccountId; }
    String getTransactionReference() { return transactionReference; }
    String getTransactionType() { return transactionType; }
    String getMerchantName() { return merchantName; }
    String getCategory() { return category; }
    BigDecimal getAmount() { return amount; }
    String getCurrencyCode() { return currencyCode; }
    String getStatus() { return status; }
    OffsetDateTime getOccurredAt() { return occurredAt; }
}
