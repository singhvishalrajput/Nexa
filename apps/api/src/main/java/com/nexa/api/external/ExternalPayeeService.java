package com.nexa.api.external;

import com.nexa.api.beans.BankingModels.Beneficiary;
import com.nexa.api.exep.ConflictException;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.service.CurrentUserProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** External payees are immutable; changing a destination requires a separately reviewed new payee. */
@Service
public class ExternalPayeeService {
  public record SaveRequest(String displayName, String recipientName, String bankName,
      String accountNumber, String accountNumberConfirmation, String ifsc) {
    @Override public String toString() { return "ExternalPayeeSaveRequest[bank details redacted]"; }
  }

  public record Payee(String id, String ownerId, String displayName, String recipientName,
      String bankName, String accountNumber, String accountNumberMasked, String ifsc,
      String status, String fingerprint) {
    @Override public String toString() { return "ExternalPayee[id=" + id + ", bank details redacted]"; }
  }

  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final ExternalAccountCipher cipher;

  public ExternalPayeeService(JdbcTemplate db, CurrentUserProvider user, ExternalAccountCipher cipher) {
    this.db = db; this.user = user; this.cipher = cipher;
  }

  @Transactional
  public Beneficiary create(SaveRequest request) {
    if (request == null) throw new InvalidRequestException("Enter the recipient's bank details.");
    String display = text(request.displayName(), "Payee name", 160);
    String recipient = text(request.recipientName(), "Account-holder name", 400);
    if (recipient.codePointCount(0, recipient.length()) > 100 || !recipient.matches("[\\p{L}\\p{M} ]+"))
      throw new InvalidRequestException("Account-holder name must contain up to 100 letters and spaces.");
    String bank = text(request.bankName(), "Bank name", 100);
    String account = normalize(request.accountNumber());
    String confirmation = normalize(request.accountNumberConfirmation());
    String ifsc = normalize(request.ifsc());
    if (!account.matches("[A-Z0-9]{9,18}"))
      throw new InvalidRequestException("Enter a bank account number with 9 to 18 letters or digits.");
    if (!account.equals(confirmation)) throw new InvalidRequestException("The bank account numbers must match.");
    if (!ifsc.matches("[A-Z]{4}0[A-Z0-9]{6}"))
      throw new InvalidRequestException("Enter a valid 11-character IFSC, for example TEST0123456.");
    String owner = user.userId();
    db.queryForObject("SELECT user_id FROM customers WHERE user_id=? FOR UPDATE", String.class, owner);
    String fingerprint = cipher.fingerprint(owner, account, ifsc);
    if (db.queryForObject("SELECT COUNT(*) FROM external_bank_payees WHERE user_id=? AND destination_hash=?",
        Integer.class, owner, fingerprint) > 0)
      throw new ConflictException("This bank account and IFSC are already saved in your payees.");
    String id = "EP-" + UUID.randomUUID();
    String encrypted = cipher.encrypt(owner, id, account);
    db.update("INSERT INTO external_bank_payees(id,user_id,display_name,recipient_name,bank_name,"
            + "account_encrypted,account_masked,ifsc,destination_hash,status) VALUES(?,?,?,?,?,?,?,?,?,'ACTIVE')",
        id, owner, display, recipient, bank, encrypted, "•••• " + account.substring(account.length() - 4), ifsc, fingerprint);
    return detail(id);
  }

  @Transactional(readOnly = true)
  public List<Beneficiary> list() {
    return db.query("SELECT id,display_name,bank_name,account_masked,status,ifsc,recipient_name"
            + " FROM external_bank_payees WHERE user_id=? ORDER BY display_name,id FETCH NEXT 100 ROWS ONLY",
        (r, n) -> new Beneficiary(r.getString(1), r.getString(2), r.getString(3), r.getString(4),
            r.getString(5), "EXTERNAL_BANK", r.getString(6), r.getString(7)), user.userId());
  }

  @Transactional(readOnly = true, noRollbackFor = ResourceNotFoundException.class)
  public Beneficiary detail(String id) {
    var rows = db.query("SELECT id,display_name,bank_name,account_masked,status,ifsc,recipient_name"
            + " FROM external_bank_payees WHERE id=? AND user_id=?",
        (r, n) -> new Beneficiary(r.getString(1), r.getString(2), r.getString(3), r.getString(4),
            r.getString(5), "EXTERNAL_BANK", r.getString(6), r.getString(7)), id, user.userId());
    if (rows.isEmpty()) throw new ResourceNotFoundException("The external payee was not found.");
    return rows.get(0);
  }

  /** Internal only. A caller requesting a lock must already own the surrounding transaction. */
  public Payee requireOwned(String id, String owner, boolean lock) {
    var rows = db.query("SELECT * FROM external_bank_payees WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),
        (r, n) -> new Payee(r.getString("id"), owner, r.getString("display_name"), r.getString("recipient_name"),
            r.getString("bank_name"), cipher.decrypt(owner, id, r.getString("account_encrypted")),
            r.getString("account_masked"), r.getString("ifsc"), r.getString("status"), r.getString("destination_hash")), id, owner);
    if (rows.isEmpty()) throw new ResourceNotFoundException("The external payee was not found.");
    Payee payee = rows.get(0);
    if (!cipher.fingerprint(owner, payee.accountNumber(), payee.ifsc()).equals(payee.fingerprint()))
      throw new InvalidRequestException("This payee's bank details changed. Save and review a new payee.");
    return payee;
  }

  private static String normalize(String value) { return value == null ? "" : value.strip().toUpperCase(Locale.ROOT); }
  private static String text(String value, String field, int limit) {
    String clean = value == null ? "" : value.strip();
    if (clean.isBlank() || clean.getBytes(StandardCharsets.UTF_8).length > limit
        || clean.codePoints().anyMatch(Character::isISOControl))
      throw new InvalidRequestException(field + " is required and must fit within " + limit + " bytes without control characters.");
    return clean;
  }
}
