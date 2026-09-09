package com.nexa.api.accounts;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "BANK_ACCOUNTS")
class BankAccountEntity {

    @Id
    @Column(name = "ID", length = 26, nullable = false)
    private String id;

    @Column(name = "USER_ID", length = 26, nullable = false, unique = true)
    private String userId;

    @Column(name = "ACCOUNT_NUMBER", length = 20, nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "ACCOUNT_NUMBER_MASKED", length = 32, nullable = false)
    private String accountNumberMasked;

    @Column(name = "DISPLAY_NAME", length = 120, nullable = false)
    private String displayName;

    @Column(name = "ACCOUNT_TYPE", length = 24, nullable = false)
    private String accountType;

    @Column(name = "CURRENCY_CODE", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "AVAILABLE_BALANCE", precision = 19, scale = 4, nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "LEDGER_BALANCE", precision = 19, scale = 4, nullable = false)
    private BigDecimal ledgerBalance;

    @Column(name = "STATUS", length = 24, nullable = false)
    private String status;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    protected BankAccountEntity() {
    }

    BankAccountEntity(
            String id,
            String userId,
            String accountNumber,
            String accountNumberMasked,
            String displayName,
            String accountType,
            String currencyCode,
            BigDecimal openingBalance,
            OffsetDateTime now) {
        this.id = id;
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.accountNumberMasked = accountNumberMasked;
        this.displayName = displayName;
        this.accountType = accountType;
        this.currencyCode = currencyCode;
        this.availableBalance = openingBalance;
        this.ledgerBalance = openingBalance;
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }

    String getId() { return id; }
    String getUserId() { return userId; }
    String getAccountNumber() { return accountNumber; }
    String getAccountNumberMasked() { return accountNumberMasked; }
    String getDisplayName() { return displayName; }
    String getAccountType() { return accountType; }
    String getCurrencyCode() { return currencyCode; }
    BigDecimal getAvailableBalance() { return availableBalance; }
    BigDecimal getLedgerBalance() { return ledgerBalance; }
    String getStatus() { return status; }
    OffsetDateTime getUpdatedAt() { return updatedAt; }
    Long getVersion() { return version; }
}
