package com.nexa.api.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 160) String fullName,
        @Size(max = 32) String phoneNumber,
        @Size(max = 255) String address) {
}
