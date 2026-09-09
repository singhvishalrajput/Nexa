package com.nexa.api.transactions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

@Service
public class TransactionRecordingService {

    private final TransactionRepository transactionRepository;

    TransactionRecordingService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void recordOpeningCredit(
            String transactionId,
            String bankAccountId,
            String journalEntryId,
            String reference,
            BigDecimal amount,
            String currencyCode,
            OffsetDateTime now) {
        transactionRepository.save(new TransactionEntity(
                transactionId,
                bankAccountId,
                journalEntryId,
                reference,
                "DEMO_OPENING_CREDIT",
                "Nexa",
                "Opening credit",
                amount,
                currencyCode,
                "POSTED",
                now));
    }
}
