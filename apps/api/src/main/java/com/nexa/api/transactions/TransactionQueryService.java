package com.nexa.api.transactions;

import com.nexa.api.accounts.AccountQueryService;
import com.nexa.api.core.model.*;
import com.nexa.api.core.repository.TransactionDao;
import com.nexa.api.identity.CurrentUserProvider;
import com.nexa.api.shared.errors.*;
import com.nexa.api.shared.web.PageResponse;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionQueryService {
  private final AccountQueryService accounts;
  private final TransactionDao transactions;
  private final CurrentUserProvider user;

  public TransactionQueryService(
      AccountQueryService accounts, TransactionDao transactions, CurrentUserProvider user) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.user = user;
  }

  public TransactionResponse detail(String id) {
    var t =
        transactions
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("The transaction was not found."));
    Account a =
        owned(t.getSourceAccount())
            ? t.getSourceAccount()
            : owned(t.getDestinationAccount()) ? t.getDestinationAccount() : null;
    if (a == null) throw new ResourceNotFoundException("The transaction was not found.");
    return response(t, a);
  }

  private boolean owned(Account a) {
    return a != null
        && a.getCustomer() != null
        && user.userId().equals(a.getCustomer().getUserId());
  }

  public PageResponse<TransactionResponse> transactions(
      String id, String category, LocalDate from, LocalDate to, int page, int size) {
    return transactions(id, category, from, to, page, size, null, null);
  }

  public PageResponse<TransactionResponse> transactions(
      String id,
      String category,
      LocalDate from,
      LocalDate to,
      int page,
      int size,
      String direction,
      String search) {
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw new InvalidRequestException("Invalid page or size.");
    if (direction != null && !Set.of("DEBIT", "CREDIT").contains(direction))
      throw new InvalidRequestException("Direction must be DEBIT or CREDIT.");
    if (search != null && search.length() > 100)
      throw new InvalidRequestException("Search is too long.");
    if (from != null && to != null && from.isAfter(to))
      throw new InvalidRequestException("The from date must not be after the to date.");
    Account a = accounts.requireOwnedEntity(id);
    Specification<BankTransaction> spec =
        (r, q, b) ->
            b.or(
                b.equal(r.get("sourceAccount").get("id"), a.getId()),
                b.equal(r.get("destinationAccount").get("id"), a.getId()));
    if (category != null && !category.isBlank())
      spec =
          spec.and(
              (r, q, b) ->
                  b.equal(b.lower(r.get("category")), category.trim().toLowerCase(Locale.ROOT)));
    if (from != null)
      spec = spec.and((r, q, b) -> b.greaterThanOrEqualTo(r.get("createdAt"), from.atStartOfDay()));
    if (to != null)
      spec = spec.and((r, q, b) -> b.lessThan(r.get("createdAt"), to.plusDays(1).atStartOfDay()));
    if (direction != null)
      spec =
          spec.and(
              (r, q, b) ->
                  b.equal(
                      r.get(direction.equals("DEBIT") ? "sourceAccount" : "destinationAccount")
                          .get("id"),
                      a.getId()));
    if (search != null && !search.isBlank()) {
      String term =
          "%"
              + search
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%";
      spec =
          spec.and(
              (r, q, b) ->
                  b.or(
                      b.like(b.lower(r.get("merchantName")), term, '!'),
                      b.like(b.lower(r.get("id")), term, '!')));
    }
    return PageResponse.from(
        transactions
            .findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
            .map(t -> response(t, a)));
  }

  private TransactionResponse response(BankTransaction t, Account a) {
    boolean debit = t.getSourceAccount() != null && t.getSourceAccount().getId().equals(a.getId());
    return new TransactionResponse(
        t.getId(),
        a.getId().toString(),
        t.getId(),
        t.getTransactionType().name(),
        t.getMerchantName(),
        t.getCategory(),
        debit ? t.getAmount().negate() : t.getAmount(),
        a.getCurrencyCode(),
        t.getStatus().name(),
        t.getCreatedAt().atOffset(ZoneOffset.UTC),
        t.getPaymentMethod());
  }
}
