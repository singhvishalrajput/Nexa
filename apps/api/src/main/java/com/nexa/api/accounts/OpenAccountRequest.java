package com.nexa.api.accounts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenAccountRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Pattern(regexp = "SAVINGS|CURRENT", message = "Account type must be SAVINGS or CURRENT.")
        String accountType,
        @NotBlank @Pattern(regexp = "INR", message = "Nexa demo accounts currently support INR only.")
        String currencyCode) {
}
