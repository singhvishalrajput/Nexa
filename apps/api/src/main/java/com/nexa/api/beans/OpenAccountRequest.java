package com.nexa.api.beans;


import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record OpenAccountRequest(
    @NotBlank @Size(max = 100) String displayName,
    @NotBlank @Pattern(regexp = "SAVINGS|CURRENT") String accountType,
    @NotBlank @Pattern(regexp = "INR") String currencyCode,
    @NotNull @Past LocalDate dateOfBirth,
    @NotBlank @Size(max = 255) String address) {}
