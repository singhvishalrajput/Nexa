package com.nexa.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nexa.api.security.JwtService;
import com.nexa.api.security.JwtService.IssuedAccessToken;
import com.nexa.api.shared.errors.ConflictException;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                Duration.ofDays(7));
    }

    @Test
    void rejectsDuplicateRegistrationBeforeWritingAnything() {
        RegisterRequest request = new RegisterRequest(
                "Vishal Singh", "vishal@example.com", "Strong@123", null);
        when(userRepository.existsByEmailIgnoreCase("vishal@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.register(request))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginReturnsAccessAndHashedRefreshTokens() {
        UserEntity user = activeUser();
        when(userRepository.findByEmailIgnoreCase("vishal@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("NexaDemo@123", "stored-hash")).thenReturn(true);
        when(jwtService.issue(user.getId(), user.getEmail(), user.getRole()))
                .thenReturn(new IssuedAccessToken("access-token", 900));

        AuthenticationResponse response = authenticationService.login(
                new LoginRequest("VISHAL@example.com", "NexaDemo@123"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(900);

        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenHash())
                .hasSize(64)
                .isNotEqualTo(response.refreshToken());
    }

    @Test
    void refreshRotatesAndRevokesThePreviousRefreshToken() throws Exception {
        String rawToken = "current-refresh-token";
        RefreshTokenEntity currentToken = new RefreshTokenEntity(
                "rft_01JDEMO000000000000001",
                activeUser().getId(),
                sha256(rawToken),
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                OffsetDateTime.now(ZoneOffset.UTC));
        UserEntity user = activeUser();
        when(refreshTokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(currentToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.issue(user.getId(), user.getEmail(), user.getRole()))
                .thenReturn(new IssuedAccessToken("new-access-token", 900));

        AuthenticationResponse response = authenticationService.refresh(new RefreshTokenRequest(rawToken));

        assertThat(currentToken.getRevokedAt()).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isNotEqualTo(rawToken);
    }

    private UserEntity activeUser() {
        return new UserEntity(
                "usr_01JDEMO000000000000001",
                "vishal@example.com",
                "stored-hash",
                "CUSTOMER",
                "Vishal Singh",
                "+91 90000 00482",
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
