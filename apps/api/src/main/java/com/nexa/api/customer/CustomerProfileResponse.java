package com.nexa.api.customer;

public record CustomerProfileResponse(
        String id,
        String userId,
        String fullName,
        String email,
        String phoneNumber,
        String status,
        String role) {
}
