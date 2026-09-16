package com.nexa.api.beans;


import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Transfer aggregate; only its lifecycle service may transition a status. */
@Entity
@Table(name = "TRANSFERS")
class TransferEntity {
    @Id @Column(name = "ID", length = 26) private String id;
    @Column(name = "USER_ID", length = 26, nullable = false) private String userId;
    @Column(name = "SOURCE_ACCOUNT_ID", nullable = false) private Long sourceAccountId;
    @Column(name = "BENEFICIARY_ID", length = 26, nullable = false) private String beneficiaryId;
    @Column(name = "TRANSFER_REFERENCE", length = 80, nullable = false) private String transferReference;
    @Column(name = "AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal amount;
    @Column(name = "CURRENCY_CODE", length = 3, nullable = false) @JdbcTypeCode(SqlTypes.CHAR) private String currencyCode;
    @Column(name = "FEE_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal feeAmount;
    @Column(name = "STATUS", length = 32, nullable = false) private String status;
    @Column(name = "PURPOSE", length = 280) private String purpose;
    @Column(name = "SCHEDULED_FOR") private OffsetDateTime scheduledFor;
    @Column(name = "AUTHORIZED_AT") private OffsetDateTime authorizedAt;
    @Column(name = "COMPLETED_AT") private OffsetDateTime completedAt;
    @Column(name = "FAILURE_CODE", length = 80) private String failureCode;
    @Column(name = "CREATED_AT", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private OffsetDateTime updatedAt;
    @Version @Column(name = "VERSION", nullable = false) private Long version;
    protected TransferEntity() { }
}
