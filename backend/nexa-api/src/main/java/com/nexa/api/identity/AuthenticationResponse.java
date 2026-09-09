package com.nexa.api.identity;

public record AuthenticationResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        User user) {

    public record User(String id, String email, String role) {
    }
}
