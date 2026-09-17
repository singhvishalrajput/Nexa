package com.nexa.api.service;

import com.nexa.api.beans.UserEntity;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.UserRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserQueryService {

  private final UserRepository userRepository;

  UserQueryService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public UserSummary requireUser(String userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("The current user was not found."));
    return toSummary(user);
  }

  @Transactional
  public UserSummary updateProfile(String userId, String fullName, String phoneNumber) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("The current user was not found."));
    String normalizedPhoneNumber =
        phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.trim();
    user.updateProfile(fullName.trim(), normalizedPhoneNumber, OffsetDateTime.now());
    userRepository.saveAndFlush(user);
    return toSummary(user);
  }

  private UserSummary toSummary(UserEntity user) {
    return new UserSummary(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getPhoneNumber(),
        user.getStatus(),
        user.getRole());
  }

  public record UserSummary(
      String id, String email, String fullName, String phoneNumber, String status, String role) {}
}
