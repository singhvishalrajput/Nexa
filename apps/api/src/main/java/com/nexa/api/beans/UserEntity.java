package com.nexa.api.beans;

import java.time.OffsetDateTime;

public class UserEntity {
  private String id;
  private String email;
  private String status;
  private String passwordHash;
  private String role;
  private String fullName;
  private String phoneNumber;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

  protected UserEntity() {}

  public UserEntity(
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

  public void setStatus(String value) {
    status = value;
  }

  public String getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getStatus() {
    return status;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getRole() {
    return role;
  }

  public String getFullName() {
    return fullName;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void updateProfile(String fullName, String phoneNumber, OffsetDateTime now) {
    this.fullName = fullName;
    this.phoneNumber = phoneNumber;
    this.updatedAt = now;
  }
}
