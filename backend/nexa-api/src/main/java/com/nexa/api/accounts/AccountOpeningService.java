package com.nexa.api.accounts;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexa.api.identity.CurrentUserProvider;
import com.nexa.api.identity.UserQueryService;
import com.nexa.api.ledger.OpeningLedgerService;
import com.nexa.api.shared.errors.ConflictException;
import com.nexa.api.transactions.TransactionRecordingService;

@Service
public class AccountOpeningService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final CurrentUserProvider currentUserProvider;
    private final UserQueryService userQueryService;
    private final BankAccountRepository bankAccountRepository;
    private final OpeningLedgerService openingLedgerService;
    private final TransactionRecordingService transactionRecordingService;
    private final BigDecimal demoOpeningCredit;

    AccountOpeningService(
            CurrentUserProvider currentUserProvider,
            UserQueryService userQueryService,
            BankAccountRepository bankAccountRepository,
            OpeningLedgerService openingLedgerService,
            TransactionRecordingService transactionRecordingService,
            @Value("${nexa.banking.demo-opening-credit}") BigDecimal demoOpeningCredit) {
        this.currentUserProvider = currentUserProvider;
        this.userQueryService = userQueryService;
        this.bankAccountRepository = bankAccountRepository;
        this.openingLedgerService = openingLedgerService;
        this.transactionRecordingService = transactionRecordingService;
        this.demoOpeningCredit = demoOpeningCredit;
    }

    @Transactional
    public AccountResponse open(OpenAccountRequest request) {
        String userId = currentUserProvider.userId();
        UserQueryService.UserSummary user = userQueryService.requireUser(userId);
        if (!"CUSTOMER".equals(user.role())) {
            throw new ConflictException("Only customers can open a Nexa bank account.");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String accountId = secureId("acc_");
        String accountNumber = uniqueAccountNumber();
        String lastFour = accountNumber.substring(accountNumber.length() - 4);
        BankAccountEntity account = new BankAccountEntity(
                accountId,
                userId,
                accountNumber,
                "•••• " + lastFour,
                request.displayName().trim(),
                request.accountType(),
                request.currencyCode(),
                demoOpeningCredit,
                now);

        try {
            bankAccountRepository.saveAndFlush(account);
            String journalEntryId = openingLedgerService.recordOpeningCredit(
                    secureId("lda_"),
                    secureId("jen_"),
                    secureId("ldp_"),
                    secureId("ldp_"),
                    accountId,
                    accountNumber,
                    demoOpeningCredit,
                    request.currencyCode(),
                    now);
            transactionRecordingService.recordOpeningCredit(
                    secureId("txn_"),
                    accountId,
                    journalEntryId,
                    "OPENING-" + accountId,
                    demoOpeningCredit,
                    request.currencyCode(),
                    now);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("A Nexa bank account could not be opened because its account details already exist.");
        }
        return toResponse(account);
    }

    private String uniqueAccountNumber() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder value = new StringBuilder("9");
            while (value.length() < 12) value.append(SECURE_RANDOM.nextInt(10));
            String candidate = value.toString();
            if (!bankAccountRepository.existsByAccountNumber(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to allocate a unique Nexa account number.");
    }

    private String secureId(String prefix) {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return prefix + BASE64_URL.encodeToString(bytes);
    }

    private AccountResponse toResponse(BankAccountEntity account) {
        return new AccountResponse(
                account.getId(),
                account.getDisplayName(),
                account.getAccountNumberMasked(),
                account.getAccountType(),
                account.getCurrencyCode(),
                account.getAvailableBalance(),
                account.getLedgerBalance(),
                account.getStatus(),
                account.getUpdatedAt());
    }
}
