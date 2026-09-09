package com.nexa.api.transactions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionResponse(
        String id,
        String accountId,
        String reference,
        String type,
        String merchantName,
        String category,
        BigDecimal amount,
        String currencyCode,
        String status,
        OffsetDateTime occurredAt) {
}
