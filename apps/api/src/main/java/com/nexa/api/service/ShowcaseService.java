package com.nexa.api.service;

import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compatibility boundary: internal payee transfers post; external provider actions stay explicit
 * simulations.
 */
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
  private final MoneyTransferService transfers;
  private final BeneficiaryQueryService beneficiaries;

  public ShowcaseService(
      JdbcTemplate db,
      CurrentUserProvider user,
      ActionPreparationService preparation,
      CardQueryService cards,
      MoneyTransferService transfers,
      BeneficiaryQueryService beneficiaries) {
    this.db = db;
    this.user = user;
    this.preparation = preparation;
    this.cards = cards;
    this.transfers = transfers;
    this.beneficiaries = beneficiaries;
  }

  private void validate(String operation, String account, String target, String amount) {
    if (Set.of("FREEZE_CARD", "UNFREEZE_CARD", "REPLACE_CARD").contains(operation)) {
      var card = cards.detail(target); // Ownership is checked even for simulated actions.
      if ("CLOSED".equals(card.status())) throw new InvalidRequestException("Choose an open card.");
    } else {
      preparation.prepare(
          operation, account, target, amount == null ? null : new BigDecimal(amount));
    }
  }

  @Transactional(noRollbackFor = {InvalidRequestException.class, ResourceNotFoundException.class})
  public Receipt prepare(String operation, String account, String target, BigDecimal amount) {
    if ("START_TRANSFER".equals(operation)) {
      var receipt =
          transfers.prepare(
              new com.nexa.api.controller.MoneyTransferController.Request(
                  account, null, beneficiaries.destinationNumber(target), amount));
      db.update(
          "UPDATE transactions SET operation='START_TRANSFER',target_id=? WHERE id=? AND"
              + " record_kind='TRANSFER_REVIEW'",
          target,
          receipt.id());
      return read(receipt.id(), false);
    }
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
        "INSERT INTO transactions"
            + " (record_kind,id,user_id,operation,source_account_id,target_id,amount,currency_code,status,expires_at)"
            + " VALUES ('SIMULATION',?,?,?,?,?,?,?,'REVIEW',?)",
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
    if (!receipt.simulated()) {
      if (receipt.status().equals("COMPLETED")) return receipt;
      if (!receipt.status().equals("REVIEW"))
        throw new InvalidRequestException("This transfer review is closed.");
      // The reviewed account is immutable; payee edits never redirect an existing review.
      transfers.confirm(id);
      return read(id, false);
    }
    if ("START_TRANSFER".equals(receipt.operation()))
      throw new InvalidRequestException(
          "This old request was only a simulation. Review a new transfer to a linked Nexa payee.");
    if (receipt.status().equals("SIMULATED")) return receipt;
    if (!receipt.status().equals("REVIEW"))
      throw new InvalidRequestException("This request is closed. Start a new request.");
    validate(receipt.operation(), receipt.accountId(), receipt.targetId(), receipt.amount());
    db.update(
        "UPDATE transactions SET status='SIMULATED',completed_at=? WHERE id=?",
        Timestamp.from(Instant.now()),
        id);
    return read(id, false);
  }

  @Transactional
  public Receipt cancel(String id) {
    Receipt receipt = read(id, true);
    if (receipt.status().equals("REVIEW"))
      db.update("UPDATE transactions SET status='CANCELLED' WHERE id=?", id);
    return read(id, false);
  }

  public Receipt status(String id) {
    return read(id, false);
  }

  public List<Receipt> history() {
    return db
        .query(
            "SELECT id FROM transactions WHERE (record_kind='SIMULATION' OR"
                + " (record_kind='TRANSFER_REVIEW' AND operation='START_TRANSFER')) AND user_id=?"
                + " ORDER BY expires_at DESC FETCH NEXT 30 ROWS ONLY",
            (r, n) -> r.getString(1),
            user.userId())
        .stream()
        .map(id -> read(id, false))
        .toList();
  }

  private Receipt read(String id, boolean lock) {
    var rows =
        db.query(
            "SELECT * FROM transactions WHERE record_kind IN ('SIMULATION','TRANSFER_REVIEW') AND"
                + " id=? AND user_id=?"
                + (lock ? " FOR UPDATE" : ""),
            (r, n) -> {
              Instant expiry = r.getTimestamp("expires_at").toInstant();
              String state = r.getString("status");
              boolean simulated = r.getString("record_kind").equals("SIMULATION");
              if (!simulated && state.equals("READY")) state = "REVIEW";
              if (state.equals("REVIEW") && !Instant.now().isBefore(expiry)) state = "EXPIRED";
              var completed = r.getTimestamp("completed_at");
              return new Receipt(
                  r.getString("id"),
                  r.getString("operation"),
                  r.getString("source_account_id"),
                  r.getString("target_id"),
                  r.getString("amount"),
                  r.getString("currency_code"),
                  state,
                  expiry.toString(),
                  completed == null ? null : completed.toInstant().toString(),
                  simulated
                      ? (state.equals("SIMULATED") ? "DEMO-" + id : null)
                      : r.getString("transaction_reference"),
                  simulated);
            },
            id,
            user.userId());
    if (rows.isEmpty()) throw new ResourceNotFoundException("Request not found.");
    return rows.get(0);
  }
}
