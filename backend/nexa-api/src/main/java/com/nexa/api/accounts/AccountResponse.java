package com.nexa.api.accounts;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountResponse(
        String id,
        String displayName,
        String accountNumberMasked,
        String accountType,
        String currencyCode,
        BigDecimal availableBalance,
        BigDecimal ledgerBalance,
        String status,
        OffsetDateTime updatedAt) {
}
