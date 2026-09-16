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
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;

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

  /** Shared execution path for conversational and API filtering. Boundaries are converted to UTC only here. */
  public TransactionQueryResult query(TransactionQuery query) {
    if (query.dateRange() == null)
      throw new InvalidRequestException("A transaction query must include a resolved date range.");
    Specification<BankTransaction> spec = (r, q, b) -> ownedPredicate(r, b, query.direction());
    var range = query.dateRange();
    LocalDateTime start = LocalDateTime.ofInstant(range.start().toInstant(), ZoneOffset.UTC);
    LocalDateTime end = LocalDateTime.ofInstant(range.end().toInstant(), ZoneOffset.UTC);
    spec = spec.and((r, q, b) -> b.greaterThanOrEqualTo(r.get("createdAt"), start))
        .and((r, q, b) -> b.lessThan(r.get("createdAt"), end));
    if (!query.statuses().isEmpty()) spec = spec.and((r, q, b) -> r.get("status").in(query.statuses()));
    if (query.minAmount() != null) spec = spec.and((r, q, b) -> b.greaterThanOrEqualTo(r.get("amount"), query.minAmount()));
    if (query.maxAmount() != null) spec = spec.and((r, q, b) -> b.lessThanOrEqualTo(r.get("amount"), query.maxAmount()));
    // Core records use TRANSFER only for account-to-account movements; spending excludes those.
    if (query.excludeOwnTransfers())
      spec = spec.and((r, q, b) -> b.notEqual(r.get("transactionType"), TransactionType.TRANSFER));
    if (query.condition() != null) spec = spec.and((r, q, b) -> condition(query.condition(), r, b));
    var rows = transactions.findAll(spec, PageRequest.of(0, query.limit(), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    var data = rows.map(t -> response(t, owned(t.getSourceAccount()) ? t.getSourceAccount() : t.getDestinationAccount())).toList();
    BigDecimal total = data.stream().map(TransactionResponse::amount).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new TransactionQueryResult(query, data, total);
  }

  private Predicate ownedPredicate(Root<BankTransaction> r, CriteriaBuilder b, TransactionQuery.Direction direction) {
    Predicate source = b.equal(r.get("sourceAccount").get("customer").get("userId"), user.userId());
    Predicate destination = b.equal(r.get("destinationAccount").get("customer").get("userId"), user.userId());
    return switch (direction) { case OUTGOING -> source; case INCOMING -> destination; case ANY -> b.or(source, destination); };
  }

  private Predicate condition(TransactionQuery.Condition node, Root<BankTransaction> r, CriteriaBuilder b) {
    if (node instanceof TransactionQuery.Group group) {
      Predicate[] children = group.children().stream().map(c -> condition(c, r, b)).toArray(Predicate[]::new);
      return group.join() == TransactionQuery.Join.AND ? b.and(children) : b.or(children);
    }
    var p = (TransactionQuery.Predicate) node;
    if (p.field() == TransactionQuery.Field.LOCATION)
      throw new InvalidRequestException("Location filtering is not available for transaction records.");
    Path<?> field = switch (p.field()) {
      case MERCHANT -> r.get("merchantName");
      case CATEGORY -> r.get("category");
      case PAYMENT_METHOD -> r.get("paymentMethod");
      case STATUS -> r.get("status");
      case ACCOUNT -> r.get("sourceAccount").get("id");
      case AMOUNT -> r.get("amount");
      case LOCATION -> throw new IllegalStateException();
    };
    if (p.field() == TransactionQuery.Field.AMOUNT) {
      BigDecimal value = new BigDecimal(p.values().get(0));
      @SuppressWarnings("unchecked") Expression<BigDecimal> amount = (Expression<BigDecimal>) field;
      return switch (p.operator()) { case GT -> b.greaterThan(amount, value); case GTE -> b.greaterThanOrEqualTo(amount, value); case LT -> b.lessThan(amount, value); case LTE -> b.lessThanOrEqualTo(amount, value); case EQ -> b.equal(amount, value); case NE -> b.notEqual(amount, value); default -> throw new InvalidRequestException("Amount supports comparison operators only."); };
    }
    Expression<String> value = b.lower(field.as(String.class));
    List<String> values = p.values().stream().map(v -> v.toLowerCase(Locale.ROOT)).toList();
    return switch (p.operator()) {
      case EQ -> b.equal(value, values.get(0)); case NE -> b.notEqual(value, values.get(0));
      case IN -> value.in(values); case NOT_IN -> b.not(value.in(values));
      default -> throw new InvalidRequestException("Unsupported text comparison.");
    };
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
