package com.nexa.api.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexa.api.security.JwtService;
import com.nexa.api.security.JwtService.IssuedAccessToken;
import com.nexa.api.shared.errors.ConflictException;
import com.nexa.api.shared.errors.InvalidRequestException;
import com.nexa.api.shared.errors.UnauthorizedException;

@Service
public class AuthenticationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Duration refreshTokenTtl;

    AuthenticationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${nexa.security.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        validatePasswordStrength(request.password());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserEntity user = new UserEntity(
                secureId("usr_"),
                email,
                passwordEncoder.encode(request.password()),
                "CUSTOMER",
                request.fullName().trim(),
                normalizePhone(request.phoneNumber()),
                now);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("An account with this email already exists.");
        }
        return issueSession(user, now);
    }

    @Transactional
    public AuthenticationResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        requireActive(user);
        return issueSession(user, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public AuthenticationResponse refresh(RefreshTokenRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RefreshTokenEntity currentToken = refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .orElseThrow(this::invalidRefreshToken);
        if (currentToken.getRevokedAt() != null || !currentToken.getExpiresAt().isAfter(now)) {
            throw invalidRefreshToken();
        }
        UserEntity user = userRepository.findById(currentToken.getUserId())
                .orElseThrow(this::invalidRefreshToken);
        requireActive(user);

        currentToken.revoke(now);
        return issueSession(user, now);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.revoke(now));
    }

    private AuthenticationResponse issueSession(UserEntity user, OffsetDateTime now) {
        IssuedAccessToken accessToken = jwtService.issue(user.getId(), user.getEmail(), user.getRole());
        String rawRefreshToken = randomToken();
        refreshTokenRepository.save(new RefreshTokenEntity(
                secureId("rft_"),
                user.getId(),
                hashToken(rawRefreshToken),
                now.plus(refreshTokenTtl),
                now));
        return new AuthenticationResponse(
                accessToken.value(),
                rawRefreshToken,
                "Bearer",
                accessToken.expiresInSeconds(),
                new AuthenticationResponse.User(user.getId(), user.getEmail(), user.getRole()));
    }

    private void validatePasswordStrength(String password) {
        boolean strong = password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(character -> !Character.isLetterOrDigit(character));
        if (!strong) {
            throw new InvalidRequestException(
                    "Password must include uppercase, lowercase, number, and special characters.");
        }
    }

    private void requireActive(UserEntity user) {
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("This user account is not active.");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phoneNumber) {
        return phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.trim();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_URL.encodeToString(bytes);
    }

    private String secureId(String prefix) {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return prefix + BASE64_URL.encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("The email or password is incorrect.");
    }

    private UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException("The refresh token is invalid, expired, or revoked.");
    }
}
