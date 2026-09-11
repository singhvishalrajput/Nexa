package com.nexa.api.core.model;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "customers")
public class Customer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(unique = true, length = 26)
  private String userId;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String value) {
    userId = value;
  }

  private String fullName;
  private String email;
  private String phoneNumber;
  private LocalDate dateOfBirth;
  private String address;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @PrePersist
  void created() {
    createdAt = updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  void updated() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String v) {
    fullName = v;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String v) {
    email = v;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String v) {
    phoneNumber = v;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate v) {
    dateOfBirth = v;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String v) {
    address = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
