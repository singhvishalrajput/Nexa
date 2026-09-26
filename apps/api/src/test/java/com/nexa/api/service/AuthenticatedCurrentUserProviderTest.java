package com.nexa.api.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexa.api.beans.UserEntity;
import com.nexa.api.exep.UnauthorizedException;
import com.nexa.api.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticatedCurrentUserProviderTest {
  private final UserRepository users = mock(UserRepository.class);
  private final AuthenticatedCurrentUserProvider current = new AuthenticatedCurrentUserProvider(users);

  @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

  @Test void aPreviouslyAuthenticatedPrincipalCannotAccessADisabledProfile() {
    authenticate();
    UserEntity user = user();
    when(users.findById("owner")).thenReturn(Optional.of(user));
    assertThat(current.userId()).isEqualTo("owner");
    user.setStatus("DISABLED");
    assertThatThrownBy(current::userId).isInstanceOf(UnauthorizedException.class);
  }

  @Test void removedCredentialsInvalidateAnOtherwiseAuthenticatedPrincipal() {
    authenticate();
    when(users.findById("owner")).thenReturn(Optional.empty());
    assertThatThrownBy(current::userId).isInstanceOf(UnauthorizedException.class);
  }

  private void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("owner", "unused", List.of()));
  }
  private UserEntity user() {
    return new UserEntity("owner", "synthetic@example.test", "unused", "CUSTOMER",
        "Synthetic customer", null, OffsetDateTime.now());
  }
}