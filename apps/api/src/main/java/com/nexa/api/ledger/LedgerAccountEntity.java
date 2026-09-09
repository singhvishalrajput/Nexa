package com.nexa.api.ledger;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "LEDGER_ACCOUNTS")
class LedgerAccountEntity {

    @Id
    @Column(name = "ID", length = 26, nullable = false)
    private String id;

    @Column(name = "BANK_ACCOUNT_ID", length = 26)
    private String bankAccountId;

    @Column(name = "ACCOUNT_CODE", length = 80, nullable = false, unique = true)
    private String accountCode;

    @Column(name = "ACCOUNT_NAME", length = 160, nullable = false)
    private String accountName;

    @Column(name = "ACCOUNT_TYPE", length = 24, nullable = false)
    private String accountType;

    @Column(name = "CURRENCY_CODE", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    protected LedgerAccountEntity() {
    }

    LedgerAccountEntity(
            String id,
            String bankAccountId,
            String accountCode,
            String accountName,
            String accountType,
            String currencyCode,
            OffsetDateTime createdAt) {
        this.id = id;
        this.bankAccountId = bankAccountId;
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.accountType = accountType;
        this.currencyCode = currencyCode;
        this.createdAt = createdAt;
    }

    String getId() { return id; }
}
