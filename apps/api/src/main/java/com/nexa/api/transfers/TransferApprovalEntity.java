package com.nexa.api.transfers;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stores a hash of a one-time approval challenge, never the challenge itself. */
@Entity
@Table(name = "TRANSFER_APPROVALS")
class TransferApprovalEntity {
    @Id @Column(name = "ID", length = 26) private String id;
    @Column(name = "TRANSFER_ID", length = 26, nullable = false) private String transferId;
    @Column(name = "USER_ID", length = 26, nullable = false) private String userId;
    @Column(name = "CHALLENGE_HASH", columnDefinition = "CHAR(64)", nullable = false) private String challengeHash;
    @Column(name = "STATUS", length = 24, nullable = false) private String status;
    @Column(name = "EXPIRES_AT", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "USED_AT") private OffsetDateTime usedAt;
    @Column(name = "CREATED_AT", nullable = false) private OffsetDateTime createdAt;
    protected TransferApprovalEntity() { }
}
