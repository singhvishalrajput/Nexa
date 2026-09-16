package com.nexa.api.beans;


import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "AUTH_REFRESH_TOKENS")
public class RefreshTokenEntity {

    @Id
    @Column(name = "ID", length = 26, nullable = false)
    private String id;

    @Column(name = "USER_ID", length = 26, nullable = false)
    private String userId;

    @Column(name = "TOKEN_HASH", length = 64, nullable = false, unique = true)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tokenHash;

    @Column(name = "EXPIRES_AT", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "REVOKED_AT")
    private OffsetDateTime revokedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(String id, String userId, String tokenHash, OffsetDateTime expiresAt, OffsetDateTime now) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public String getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }

    public void revoke(OffsetDateTime now) {
        this.revokedAt = now;
    }
}

