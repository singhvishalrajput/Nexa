package com.nexa.api.identity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "USERS")
class UserEntity {

    @Id
    @Column(name = "ID", length = 26, nullable = false)
    private String id;

    @Column(name = "EMAIL", length = 254, nullable = false, unique = true)
    private String email;

    @Column(name = "STATUS", length = 24, nullable = false)
    private String status;

    @Column(name = "PASSWORD_HASH", length = 100)
    private String passwordHash;

    @Column(name = "ROLE", length = 24, nullable = false)
    private String role;

    @Column(name = "FULL_NAME", length = 160, nullable = false)
    private String fullName;

    @Column(name = "PHONE_NUMBER", length = 32)
    private String phoneNumber;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserEntity() {
    }

    UserEntity(
            String id,
            String email,
            String passwordHash,
            String role,
            String fullName,
            String phoneNumber,
            OffsetDateTime now) {
        this.id = id;
        this.email = email;
        this.status = "ACTIVE";
        this.passwordHash = passwordHash;
        this.role = role;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.createdAt = now;
        this.updatedAt = now;
    }

    String getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getStatus() {
        return status;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getRole() {
        return role;
    }

    String getFullName() {
        return fullName;
    }

    String getPhoneNumber() {
        return phoneNumber;
    }

    void updateProfile(String fullName, String phoneNumber, OffsetDateTime now) {
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.updatedAt = now;
    }
}
