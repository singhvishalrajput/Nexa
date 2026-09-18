package com.nexa.api.service;

import com.nexa.api.beans.UserEntity;
import com.nexa.api.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit local/operator provisioning; public registration always remains CUSTOMER. */
@Component
public class AdminBootstrap implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final String email, password;

  public AdminBootstrap(
      UserRepository users,
      PasswordEncoder encoder,
      @Value("${nexa.admin.bootstrap.email:}") String email,
      @Value("${nexa.admin.bootstrap.password:}") String password) {
    this.users = users;
    this.encoder = encoder;
    this.email = email;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (email.isBlank() && password.isBlank()) return;
    if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || password.length() < 16
        || password.length() > 72)
      throw new IllegalStateException(
          "Admin bootstrap requires an email and a password of 16–72 characters.");
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    var existing = users.findByEmailIgnoreCase(normalized);
    if (existing.isPresent()) {
      if (!"ADMIN".equals(existing.get().getRole()))
        throw new IllegalStateException(
            "Admin bootstrap cannot promote an existing customer. Choose a dedicated admin email.");
      return;
    }
    byte[] bytes = new byte[16];
    new java.security.SecureRandom().nextBytes(bytes);
    users.saveAndFlush(
        new UserEntity(
            "usr_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            normalized,
            encoder.encode(password),
            "ADMIN",
            "Nexa Administrator",
            null,
            OffsetDateTime.now(java.time.ZoneOffset.UTC)));
  }
}
