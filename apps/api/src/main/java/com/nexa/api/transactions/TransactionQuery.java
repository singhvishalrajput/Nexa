package com.nexa.api.transactions;

import com.nexa.api.core.model.TransactionStatus;
import java.math.BigDecimal;
import java.util.*;

/** Immutable, validated query AST. Every user-supplied filter reaches execution through this type. */
public record TransactionQuery(
    BusinessDateResolver.Range dateRange,
    Direction direction,
    Set<TransactionStatus> statuses,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    Condition condition,
    boolean excludeOwnTransfers,
    int limit) {
  public enum Direction { OUTGOING, INCOMING, ANY }
  public enum Join { AND, OR }
  public enum Field { MERCHANT, CATEGORY, PAYMENT_METHOD, ACCOUNT, LOCATION, STATUS, AMOUNT }
  public enum Operator { EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN }
  public sealed interface Condition permits Group, Predicate {}
  public record Group(Join join, List<Condition> children) implements Condition {
    public Group { children = List.copyOf(children); if (children.isEmpty()) throw new IllegalArgumentException("Condition group is empty."); }
  }
  public record Predicate(Field field, Operator operator, List<String> values) implements Condition {
    public Predicate { values = List.copyOf(values); if (values.isEmpty()) throw new IllegalArgumentException("Predicate has no values."); }
  }

  public TransactionQuery {
    statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    if (minAmount != null && minAmount.signum() < 0 || maxAmount != null && maxAmount.signum() < 0)
      throw new IllegalArgumentException("Amounts must be positive.");
    if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0)
      throw new IllegalArgumentException("Minimum amount cannot exceed maximum amount.");
    direction = direction == null ? Direction.ANY : direction;
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid result limit.");
  }

  public static TransactionQuery spending(BusinessDateResolver.Range range, Condition condition, BigDecimal min, BigDecimal max) {
    return new TransactionQuery(range, Direction.OUTGOING, Set.of(TransactionStatus.SUCCESS), min, max, condition, true, 30);
  }
}
