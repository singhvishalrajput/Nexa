package com.nexa.api.beans;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank @Size(max = 512) String refreshToken) {
}
