package com.nexa.api.accounts;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexa.api.identity.CurrentUserProvider;
import com.nexa.api.shared.errors.ResourceNotFoundException;

@Service
public class AccountQueryService {

    private final CurrentUserProvider currentUserProvider;
    private final BankAccountRepository bankAccountRepository;

    AccountQueryService(CurrentUserProvider currentUserProvider, BankAccountRepository bankAccountRepository) {
        this.currentUserProvider = currentUserProvider;
        this.bankAccountRepository = bankAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> currentAccounts() {
        String userId = currentUserProvider.userId();
        return bankAccountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse requireOwnedAccount(String accountId) {
        String userId = currentUserProvider.userId();
        BankAccountEntity account = bankAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("The account was not found."));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse balance(String accountId) {
        AccountResponse account = requireOwnedAccount(accountId);
        return new AccountBalanceResponse(
                account.id(),
                account.currencyCode(),
                account.availableBalance(),
                account.ledgerBalance(),
                account.updatedAt());
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
