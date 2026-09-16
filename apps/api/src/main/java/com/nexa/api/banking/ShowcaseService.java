package com.nexa.api.banking;

import com.nexa.api.identity.CurrentUserProvider;
import com.nexa.api.shared.errors.InvalidRequestException;
import com.nexa.api.shared.errors.ResourceNotFoundException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Isolated provider simulation: never posts to the ledger or calls a bank/card network. */
@Service
public class ShowcaseService {
  public record Receipt(
      String id,
      String operation,
      String accountId,
      String targetId,
      String amount,
      String currencyCode,
      String status,
      String expiresAt,
      String completedAt,
      String reference,
      boolean simulated) {}

  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final ActionPreparationService preparation;
  private final CardQueryService cards;

  public ShowcaseService(
      JdbcTemplate db,
      CurrentUserProvider user,
      ActionPreparationService preparation,
      CardQueryService cards) {
    this.db = db;
    this.user = user;
    this.preparation = preparation;
    this.cards = cards;
  }

  private void validate(String operation, String account, String target, String amount) {
    if (Set.of("FREEZE_CARD", "UNFREEZE_CARD", "REPLACE_CARD").contains(operation)) {
      var card = cards.detail(target); // Ownership is checked even for simulated actions.
      if ("CLOSED".equals(card.status()))
        throw new InvalidRequestException("Choose an open card.");
    } else {
      preparation.prepare(
          operation, account, target, amount == null ? null : new BigDecimal(amount));
    }
  }

  @Transactional
  public Receipt prepare(String operation, String account, String target, BigDecimal amount) {
    String decimal = amount == null ? null : amount.toPlainString();
    validate(operation, account, target, decimal);
    if (Set.of("FREEZE_CARD", "UNFREEZE_CARD", "REPLACE_CARD").contains(operation)) {
      account = cards.detail(target).accountId();
      decimal = null;
    } else {
      var prepared = preparation.prepare(operation, account, target, amount);
      decimal = prepared.amount();
    }
    String id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO showcase_actions"
            + " (id,user_id,operation,account_id,target_id,amount,currency_code,status,expires_at)"
            + " VALUES (?,?,?,?,?,?,?,'REVIEW',?)",
        id,
        user.userId(),
        operation,
        account,
        target,
        decimal,
        "INR",
        Timestamp.from(Instant.now().plusSeconds(600)));
    return read(id, false);
  }

  @Transactional
  public Receipt confirm(String id) {
    Receipt receipt = read(id, true);
    if (receipt.status().equals("SIMULATED")) return receipt;
    if (!receipt.status().equals("REVIEW"))
      throw new InvalidRequestException("This request is closed. Start a new request.");
    validate(receipt.operation(), receipt.accountId(), receipt.targetId(), receipt.amount());
    db.update(
        "UPDATE showcase_actions SET status='SIMULATED',completed_at=? WHERE id=?",
        Timestamp.from(Instant.now()),
        id);
    return read(id, false);
  }

  @Transactional
  public Receipt cancel(String id) {
    Receipt receipt = read(id, true);
    if (receipt.status().equals("REVIEW"))
      db.update("UPDATE showcase_actions SET status='CANCELLED' WHERE id=?", id);
    return read(id, false);
  }

  public Receipt status(String id) {
    return read(id, false);
  }

  public List<Receipt> history() {
    return db
        .query(
            "SELECT id FROM showcase_actions WHERE user_id=? ORDER BY expires_at DESC"
                + " FETCH NEXT 30 ROWS ONLY",
            (r, n) -> r.getString(1),
            user.userId())
        .stream()
        .map(id -> read(id, false))
        .toList();
  }

  private Receipt read(String id, boolean lock) {
    var rows =
        db.query(
            "SELECT * FROM showcase_actions WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),
            (r, n) -> {
              Instant expiry = r.getTimestamp("expires_at").toInstant();
              String state = r.getString("status");
              if (state.equals("REVIEW") && !Instant.now().isBefore(expiry)) state = "EXPIRED";
              var completed = r.getTimestamp("completed_at");
              return new Receipt(
                  r.getString("id"),
                  r.getString("operation"),
                  r.getString("account_id"),
                  r.getString("target_id"),
                  r.getString("amount"),
                  r.getString("currency_code"),
                  state,
                  expiry.toString(),
                  completed == null ? null : completed.toInstant().toString(),
                  state.equals("SIMULATED") ? "DEMO-" + id : null,
                  true);
            },
            id,
            user.userId());
    if (rows.isEmpty()) throw new ResourceNotFoundException("Request not found.");
    return rows.get(0);
  }
}
