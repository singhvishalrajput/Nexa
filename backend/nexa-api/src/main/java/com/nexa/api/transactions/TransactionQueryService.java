package com.nexa.api.transactions;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexa.api.accounts.AccountQueryService;
import com.nexa.api.shared.errors.InvalidRequestException;
import com.nexa.api.shared.web.PageResponse;

@Service
public class TransactionQueryService {

    private final AccountQueryService accountQueryService;
    private final TransactionRepository transactionRepository;

    TransactionQueryService(AccountQueryService accountQueryService, TransactionRepository transactionRepository) {
        this.accountQueryService = accountQueryService;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> transactions(
            String accountId,
            String category,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        accountQueryService.requireOwnedAccount(accountId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("The from date must not be after the to date.");
        }

        Specification<TransactionEntity> specification = (root, query, builder) ->
                builder.equal(root.get("bankAccountId"), accountId);

        if (category != null && !category.isBlank()) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(builder.lower(root.get("category")), category.trim().toLowerCase()));
        }
        if (from != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay().atOffset(ZoneOffset.UTC)));
        }
        if (to != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThan(root.get("occurredAt"), to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<TransactionResponse> result = transactionRepository.findAll(specification, pageable)
                .map(this::toResponse);
        return PageResponse.from(result);
    }

    private TransactionResponse toResponse(TransactionEntity transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getBankAccountId(),
                transaction.getTransactionReference(),
                transaction.getTransactionType(),
                transaction.getMerchantName(),
                transaction.getCategory(),
                transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getStatus(),
                transaction.getOccurredAt());
    }
}
