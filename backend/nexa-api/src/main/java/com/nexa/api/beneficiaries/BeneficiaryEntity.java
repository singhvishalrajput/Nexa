package com.nexa.api.beneficiaries;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Payment-service aggregate. Raw destination details are never persisted here. */
@Entity
@Table(name = "BENEFICIARIES")
class BeneficiaryEntity {
    @Id @Column(name = "ID", length = 26) private String id;
    @Column(name = "USER_ID", length = 26, nullable = false) private String userId;
    @Column(name = "DISPLAY_NAME", length = 160, nullable = false) private String displayName;
    @Column(name = "BENEFICIARY_TYPE", length = 24, nullable = false) private String beneficiaryType;
    @Column(name = "ACCOUNT_HOLDER_NAME", length = 160, nullable = false) private String accountHolderName;
    @Column(name = "DESTINATION_ACCOUNT_MASKED", length = 40, nullable = false) private String destinationAccountMasked;
    @Column(name = "DESTINATION_ACCOUNT_HASH", length = 64, nullable = false) private String destinationAccountHash;
    @Column(name = "ROUTING_CODE", length = 32) private String routingCode;
    @Column(name = "STATUS", length = 24, nullable = false) private String status;
    @Column(name = "CREATED_AT", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private OffsetDateTime updatedAt;
    @Version @Column(name = "VERSION", nullable = false) private Long version;
    protected BeneficiaryEntity() { }
}
