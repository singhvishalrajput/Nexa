package com.nexa.api.service;

import com.nexa.api.beans.BankingModels.Beneficiary;
import com.nexa.api.exep.ResourceNotFoundException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(
    readOnly = true,
    noRollbackFor = {
      ResourceNotFoundException.class,
      com.nexa.api.exep.InvalidRequestException.class
    })
public class BeneficiaryQueryService {
  private final JdbcTemplate db;
  private final CurrentUserProvider user;

  public BeneficiaryQueryService(JdbcTemplate db, CurrentUserProvider user) {
    this.db = db;
    this.user = user;
  }

  public List<Beneficiary> list() {
    return query(null);
  }

  public Beneficiary detail(String id) {
    return query(id).stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("The beneficiary was not found."));
  }

  /**
   * Only explicitly linked Nexa accounts can receive a real payment. Masked digits are not an
   * identity.
   */
  public String destinationNumber(String id) {
    detail(id);
    var rows =
        db.queryForList(
            "SELECT a.account_number FROM transactions t JOIN accounts a ON"
                + " a.id=t.destination_account_id WHERE t.id=? AND t.record_kind='BENEFICIARY' AND"
                + " t.user_id=? AND t.status='ACTIVE'",
            String.class,
            id,
            user.userId());
    if (rows.isEmpty())
      throw new com.nexa.api.exep.InvalidRequestException(
          "This payee has no verified Nexa account. Open Payees and link their full Nexa account"
              + " number. External-bank payments are unavailable.");
    return rows.get(0);
  }

  public record SaveRequest(String displayName, String accountNumber) {}

  @Transactional
  public Beneficiary save(String id, SaveRequest request) {
    if (request.displayName() == null
        || request.displayName().isBlank()
        || request.displayName().length() > 160
        || request.accountNumber() == null
        || !request.accountNumber().matches("[0-9]{6,30}"))
      throw new com.nexa.api.exep.InvalidRequestException(
          "Enter a payee name and full Nexa account number.");
    var destinations =
        db.queryForList(
            "SELECT id FROM accounts WHERE account_number=? AND account_category='CUSTOMER' AND"
                + " account_type IN ('SAVINGS','CURRENT') AND status='ACTIVE' AND"
                + " currency_code='INR'",
            Long.class,
            request.accountNumber());
    if (destinations.isEmpty())
      throw new com.nexa.api.exep.InvalidRequestException(
          "An active Nexa INR deposit account was not found.");
    long destination = destinations.get(0);
    // Serialize changes per owner so duplicate payees cannot race.
    db.queryForObject(
        "SELECT user_id FROM customers WHERE user_id=? FOR UPDATE", String.class, user.userId());
    if (id != null) detail(id);
    if (db.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE record_kind='BENEFICIARY' AND user_id=? AND"
                + " destination_account_id=? AND (? IS NULL OR id<>?)",
            Integer.class,
            user.userId(),
            destination,
            id,
            id)
        > 0)
      throw new com.nexa.api.exep.ConflictException("This Nexa account is already a saved payee.");
    String mask = "•••• " + request.accountNumber().substring(request.accountNumber().length() - 4);
    String destinationHash;
    try {
      destinationHash =
          java.util.HexFormat.of()
              .formatHex(
                  java.security.MessageDigest.getInstance("SHA-256")
                      .digest(
                          ("NEXA:" + destination)
                              .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    if (id == null) {
      id = "B-" + java.util.UUID.randomUUID();
      db.update(
          "INSERT INTO"
              + " transactions(id,record_kind,user_id,status,display_name,bank_name,destination_account_id,destination_masked,destination_hash,beneficiary_type,created_at,updated_at)"
              + " VALUES(?,'BENEFICIARY',?,'ACTIVE',?,'Nexa',?,?,?,'INTERNAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          id,
          user.userId(),
          request.displayName().trim(),
          destination,
          mask,
          destinationHash);
    } else
      db.update(
          "UPDATE transactions SET"
              + " display_name=?,bank_name='Nexa',destination_account_id=?,destination_masked=?,destination_hash=?,status='ACTIVE',updated_at=CURRENT_TIMESTAMP"
              + " WHERE id=? AND user_id=? AND record_kind='BENEFICIARY'",
          request.displayName().trim(),
          destination,
          mask,
          destinationHash,
          id,
          user.userId());
    return detail(id);
  }

  private List<Beneficiary> query(String id) {
    return db.query(
        "SELECT id, display_name, bank_name, destination_masked, status FROM transactions WHERE"
            + " record_kind='BENEFICIARY' AND user_id = ? AND (? IS NULL OR id = ?) ORDER BY"
            + " display_name FETCH NEXT 100 ROWS ONLY",
        (r, n) ->
            new Beneficiary(
                r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)),
        user.userId(),
        id,
        id);
  }
}
