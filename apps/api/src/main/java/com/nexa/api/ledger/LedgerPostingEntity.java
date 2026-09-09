package com.nexa.api.ledger;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "LEDGER_POSTINGS")
class LedgerPostingEntity {

    @Id
    @Column(name = "ID", length = 26, nullable = false)
    private String id;

    @Column(name = "JOURNAL_ENTRY_ID", length = 26, nullable = false)
    private String journalEntryId;

    @Column(name = "LEDGER_ACCOUNT_ID", length = 26, nullable = false)
    private String ledgerAccountId;

    @Column(name = "DIRECTION", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String direction;

    @Column(name = "AMOUNT", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "CURRENCY_CODE", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    protected LedgerPostingEntity() {
    }

    LedgerPostingEntity(
            String id,
            String journalEntryId,
            String ledgerAccountId,
            String direction,
            BigDecimal amount,
            String currencyCode,
            OffsetDateTime createdAt) {
        this.id = id;
        this.journalEntryId = journalEntryId;
        this.ledgerAccountId = ledgerAccountId;
        this.direction = direction;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.createdAt = createdAt;
    }
}
