package com.nexa.api.ledger;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

@Service
public class OpeningLedgerService {

    private static final String DEMO_FUND_CODE = "EQUITY:DEMO_OPENING_FUND";

    private final LedgerAccountRepository ledgerAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final LedgerPostingRepository ledgerPostingRepository;

    OpeningLedgerService(
            LedgerAccountRepository ledgerAccountRepository,
            JournalEntryRepository journalEntryRepository,
            LedgerPostingRepository ledgerPostingRepository) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.ledgerPostingRepository = ledgerPostingRepository;
    }

    public String recordOpeningCredit(
            String ledgerAccountId,
            String journalEntryId,
            String debitPostingId,
            String creditPostingId,
            String bankAccountId,
            String accountNumber,
            BigDecimal amount,
            String currencyCode,
            OffsetDateTime now) {
        LedgerAccountEntity demoFund = ledgerAccountRepository.findByAccountCode(DEMO_FUND_CODE)
                .orElseThrow(() -> new IllegalStateException("The demo opening fund ledger is not configured."));
        LedgerAccountEntity customerLedger = ledgerAccountRepository.save(new LedgerAccountEntity(
                ledgerAccountId,
                bankAccountId,
                "ASSET:CUSTOMER:" + accountNumber,
                "Nexa customer account " + accountNumber.substring(accountNumber.length() - 4),
                "ASSET",
                currencyCode,
                now));
        JournalEntryEntity journalEntry = journalEntryRepository.save(new JournalEntryEntity(
                journalEntryId,
                "OPENING-" + bankAccountId,
                "DEMO_OPENING_CREDIT",
                "Nexa demo opening credit",
                now));
        ledgerPostingRepository.save(new LedgerPostingEntity(
                debitPostingId,
                journalEntry.getId(),
                customerLedger.getId(),
                "D",
                amount,
                currencyCode,
                now));
        ledgerPostingRepository.save(new LedgerPostingEntity(
                creditPostingId,
                journalEntry.getId(),
                demoFund.getId(),
                "C",
                amount,
                currencyCode,
                now));
        return journalEntry.getId();
    }
}
