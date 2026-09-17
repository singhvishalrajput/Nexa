package com.nexa.api.service;

import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountCategory;
import com.nexa.api.beans.AccountStatus;
import com.nexa.api.beans.TransactionRequest;
import com.nexa.api.controller.MoneyTransferController;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.AccountDao;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The durable review ID is also the confirmation idempotency key. */
@Service
public class MoneyTransferService {
  public record Receipt(
      String id,
      String sourceAccountId,
      String sourceName,
      String sourceMasked,
      String recipientName,
      String destinationMasked,
      String amount,
      String currencyCode,
      String status,
      String reference,
      String expiresAt,
      String completedAt) {}

  private record Stored(Receipt receipt, long destinationId) {}

  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final AccountQueryService owned;
  private final AccountDao accounts;
  private final TransactionService transactions;
  private final EntityManager entities;

  public MoneyTransferService(
      JdbcTemplate db,
      CurrentUserProvider user,
      AccountQueryService owned,
      AccountDao accounts,
      TransactionService transactions,
      EntityManager entities) {
    this.db = db;
    this.user = user;
    this.owned = owned;
    this.accounts = accounts;
    this.transactions = transactions;
    this.entities = entities;
  }

  @Transactional
  public Receipt prepare(MoneyTransferController.Request request) {
    if ((request.destinationAccountId() == null) == (request.destinationAccountNumber() == null))
      throw new InvalidRequestException(
          "Choose one of your accounts or enter a Nexa account number.");
    Account source = owned.requireOwnedEntity(request.sourceAccountId());
    Account destination =
        request.destinationAccountId() != null
            ? owned.requireOwnedEntity(request.destinationAccountId())
            : accounts
                .findByAccountNumber(request.destinationAccountNumber())
                .orElseThrow(
                    () ->
                        new InvalidRequestException(
                            "We couldn’t find that Nexa account. Check the number and try again."));
    validate(source, destination, request.amount());
    Instant now = Instant.now();
    String id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO transactions"
            + " (record_kind,id,user_id,source_account_id,destination_account_id,source_name,source_masked,recipient_name,destination_masked,amount,currency_code,status,created_at,expires_at)"
            + " VALUES ('TRANSFER_REVIEW',?,?,?,?,?,?,?,?,?,?,'READY',?,?)",
        id,
        user.userId(),
        source.getId(),
        destination.getId(),
        source.getAccountName(),
        mask(source),
        destination.getCustomer().getFullName(),
        mask(destination),
        request.amount(),
        source.getCurrencyCode(),
        Timestamp.from(now),
        Timestamp.from(now.plusSeconds(300)));
    return find(id, false).receipt();
  }

  @Transactional
  public Receipt status(String id) {
    return find(id, true).receipt();
  }

  @Transactional
  public Receipt confirm(String id) {
    Stored stored = find(id, true); // Serializes double taps, HTTP retries and concurrent devices.
    Receipt receipt = stored.receipt();
    if (receipt.status().equals("COMPLETED")) return receipt;
    if (receipt.status().equals("EXPIRED"))
      throw new InvalidRequestException(
          "This review has expired. Check the details again before sending.");
    long sourceId = Long.parseLong(receipt.sourceAccountId());
    // The review already belongs to this user. Load account entities only after locking.
    // Match the ledger service's lock order, and refresh any previously loaded JPA entities.
    Account first =
        accounts.findLockedById(Math.min(sourceId, stored.destinationId())).orElseThrow();
    Account second =
        accounts.findLockedById(Math.max(sourceId, stored.destinationId())).orElseThrow();
    entities.refresh(first);
    entities.refresh(second);
    Account source = first.getId() == sourceId ? first : second;
    Account destination = first.getId() == sourceId ? second : first;
    if (source.getCustomer() == null || !user.userId().equals(source.getCustomer().getUserId()))
      throw new ResourceNotFoundException("The account was not found.");
    BigDecimal amount = new BigDecimal(receipt.amount());
    validate(source, destination, amount);
    if (!receipt.currencyCode().equals(source.getCurrencyCode()))
      throw new InvalidRequestException(
          "The account currency changed. Review this transfer again.");
    TransactionRequest request = new TransactionRequest();
    request.setSourceAccountId(sourceId);
    request.setDestinationAccountId(stored.destinationId());
    request.setAmount(amount);
    var transaction = transactions.transfer(request);
    entities.flush(); // Make the referenced transaction visible to the JDBC receipt update.
    db.update(
        "UPDATE transactions SET"
            + " status='COMPLETED',transaction_reference=?,completed_at=? WHERE id=?",
        transaction.getId(),
        Timestamp.from(Instant.now()),
        id);
    // Request completion, balance changes, journal and both ledger entries commit together.
    return find(id, false).receipt();
  }

  private void validate(Account source, Account destination, BigDecimal amount) {
    if (source.getAccountCategory() != AccountCategory.CUSTOMER
        || destination.getAccountCategory() != AccountCategory.CUSTOMER
        || source.getCustomer() == null
        || destination.getCustomer() == null)
      throw new InvalidRequestException("Choose a Nexa customer account.");
    if (source.getId().equals(destination.getId()))
      throw new InvalidRequestException("Choose two different accounts.");
    if (source.getStatus() != AccountStatus.ACTIVE
        || destination.getStatus() != AccountStatus.ACTIVE)
      throw new InvalidRequestException("Both accounts must be active before money can be sent.");
    if (!source.getCurrencyCode().equals("INR") || !destination.getCurrencyCode().equals("INR"))
      throw new InvalidRequestException("This service supports transfers in Indian rupees only.");
    if (amount == null
        || amount.signum() <= 0
        || amount.scale() > 2
        || amount.precision() - amount.scale() > 13)
      throw new InvalidRequestException(
          "Enter an amount above ₹0 with no more than two decimal places.");
    if (source.getBalance().compareTo(amount) < 0)
      throw new InvalidRequestException(
          "There isn’t enough money in this account. Enter a smaller amount.");
  }

  private Stored find(String id, boolean lock) {
    var rows =
        db.query(
            "SELECT * FROM transactions WHERE record_kind='TRANSFER_REVIEW' AND id=? AND user_id=?"
                + (lock ? " FOR UPDATE" : ""),
            (r, n) -> read(r),
            id,
            user.userId());
    if (rows.isEmpty()) throw new ResourceNotFoundException("This transfer could not be found.");
    return rows.get(0);
  }

  private Stored read(ResultSet r) throws SQLException {
    Instant expiry = r.getTimestamp("expires_at").toInstant();
    String status = r.getString("status");
    if (status.equals("READY") && !Instant.now().isBefore(expiry)) status = "EXPIRED";
    Timestamp completed = r.getTimestamp("completed_at");
    return new Stored(
        new Receipt(
            r.getString("id"),
            Long.toString(r.getLong("source_account_id")),
            r.getString("source_name"),
            r.getString("source_masked"),
            r.getString("recipient_name"),
            r.getString("destination_masked"),
            r.getBigDecimal("amount").toPlainString(),
            r.getString("currency_code"),
            status,
            r.getString("transaction_reference"),
            expiry.toString(),
            completed == null ? null : completed.toInstant().toString()),
        r.getLong("destination_account_id"));
  }

  private static String mask(Account account) {
    String number = account.getAccountNumber();
    return "•••• " + number.substring(Math.max(0, number.length() - 4));
  }
}
