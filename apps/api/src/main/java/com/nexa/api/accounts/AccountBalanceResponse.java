package com.nexa.api.accounts;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountBalanceResponse(
        String accountId,
        String currencyCode,
        BigDecimal availableBalance,
        BigDecimal ledgerBalance,
        OffsetDateTime asOf) {
}
