package com.nexa.api.service;

import com.nexa.api.beans.BankingModels;
import com.nexa.api.exep.InvalidRequestException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CardService {
  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final AccountQueryService accounts;
  private final CardQueryService cards;
  private final ActionPreparationService preparation;

  public CardService(JdbcTemplate db, CurrentUserProvider user, AccountQueryService accounts,
      CardQueryService cards, ActionPreparationService preparation) {
    this.db = db; this.user = user; this.accounts = accounts; this.cards = cards;
    this.preparation = preparation;
  }

  public BankingModels.Card createDebit(String accountId) {
    var account = accounts.requireOwnedAccount(accountId);
    if (!"ACTIVE".equals(account.status()) || !Set.of("SAVINGS", "CURRENT").contains(account.accountType()))
      throw new InvalidRequestException("Choose an active savings or current account.");
    // Serialize issuance per customer, including requests made from different sessions.
    db.queryForList("SELECT id FROM customers WHERE user_id=? FOR UPDATE", user.userId());
    var existing = cards.all();
    if (!existing.isEmpty()) {
      return existing.stream().filter(c -> "DEBIT".equals(c.cardType()) && accountId.equals(c.accountId()) && "ACTIVE".equals(c.status()))
          .findFirst().orElseThrow(() -> new InvalidRequestException("You already have cards. Contact card support for an adjustment."));
    }
    String id = "C-" + UUID.randomUUID();
    String number = "DC" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    String masked = "•••• " + String.format("%04d", new java.security.SecureRandom().nextInt(10000));
    db.update("INSERT INTO accounts(product_id,account_number,customer_id,account_name,account_type,account_category,currency_code,balance,status,created_at,updated_at,version,funding_account_id,product_status,product_type,number_masked,credit_limit,minimum_payment) SELECT ?,?,customer_id,'Nexa Digital Debit','CARD','CUSTOMER',currency_code,0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,id,'ACTIVE','DEBIT',?,0,0 FROM accounts WHERE id=?",
        id, number, masked, Long.valueOf(accountId));
    long cardAccountId = db.queryForObject("SELECT id FROM accounts WHERE product_id=?", Long.class, id);
    db.update("UPDATE accounts SET account_number=? WHERE id=?", CardNumbers.forAccount(cardAccountId, masked), cardAccountId);
    return cards.detail(id);
  }

  public String repay(String accountId, String cardId, BigDecimal amount) {
    cards.detail(cardId);
    long cardAccount = db.queryForObject("SELECT id FROM accounts WHERE product_id=? AND account_type='CARD'", Long.class, cardId);
    long source = Long.parseLong(accountId);
    java.util.stream.LongStream.of(source, cardAccount).distinct().sorted()
        .forEach(id -> db.queryForList("SELECT id FROM accounts WHERE id=? FOR UPDATE", id));
    var prepared = preparation.prepare("PAY_CARD", accountId, cardId, amount);
    var sourceRow = db.queryForMap("SELECT balance,account_type,status FROM accounts WHERE id=?", source);
    if (!Set.of("SAVINGS", "CURRENT").contains(sourceRow.get("ACCOUNT_TYPE")) || !"ACTIVE".equals(sourceRow.get("STATUS")))
      throw new InvalidRequestException("Choose an active savings or current account.");
    BigDecimal payment = new BigDecimal(prepared.amount());
    if (((BigDecimal) sourceRow.get("BALANCE")).compareTo(payment) < 0)
      throw new InvalidRequestException("Insufficient available balance.");
    String tx = "TX-" + UUID.randomUUID();
    db.update("UPDATE accounts SET balance=balance-?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", payment, source);
    db.update("UPDATE accounts SET balance=balance-?,minimum_payment=CASE WHEN minimum_payment>? THEN minimum_payment-? ELSE 0 END,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", payment, payment, payment, cardAccount);
    db.update("INSERT INTO transactions(id,record_kind,user_id,transaction_type,source_account_id,destination_account_id,amount,currency_code,status,completed_at,operation,parent_id,target_id,transaction_reference,merchant_name,category,payment_method,created_at) VALUES(?,'PAYMENT',?,'TRANSFER',?,?,?,?,'SUCCESS',CURRENT_TIMESTAMP,'CARD_PAYMENT',?,?,?,?,'CREDIT_CARD','ACCOUNT',CURRENT_TIMESTAMP)",
        tx, user.userId(), source, cardAccount, payment, prepared.currencyCode(), null, cardId, tx, cards.detail(cardId).displayName());
    db.update("INSERT INTO journal_entries(transaction_id,entry_reference,entry_type,status,created_at) VALUES(?,?,'TRANSACTION','POSTED',CURRENT_TIMESTAMP)", tx, "J" + tx);
    db.update("INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at) SELECT id,?,'DEBIT',?,CURRENT_TIMESTAMP FROM journal_entries WHERE transaction_id=?", source, payment, tx);
    db.update("INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at) SELECT id,?,'CREDIT',?,CURRENT_TIMESTAMP FROM journal_entries WHERE transaction_id=?", cardAccount, payment, tx);
    return tx;
  }

  public List<com.nexa.api.beans.AccountResponse> billFundingAccounts() {
    var result = new ArrayList<>(accounts.currentAccounts());
    db.queryForList("SELECT a.id FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE c.user_id=? AND a.account_type='CARD' AND a.product_type='CREDIT' AND a.status='ACTIVE' AND a.product_status='ACTIVE' ORDER BY a.id", Long.class, user.userId())
        .forEach(id -> {
          var a = accounts.requireOwnedEntity(id.toString());
          result.add(new com.nexa.api.beans.AccountResponse(id.toString(), a.getAccountName() + " · Credit card", a.getNumberMasked(), "CARD", a.getCurrencyCode(), a.getCreditLimit().subtract(a.getBalance()), a.getBalance(), "ACTIVE", null));
        });
    return result;
  }

  public String chargeBill(String accountId, BankingModels.Bill bill, BigDecimal amount) {
    accounts.requireOwnedEntity(accountId);
    long cardAccount = Long.parseLong(accountId);
    long cash = db.queryForObject("SELECT id FROM accounts WHERE account_category='SYSTEM' AND account_type='CASH'", Long.class);
    java.util.stream.LongStream.of(cardAccount, cash).distinct().sorted().forEach(id -> db.queryForList("SELECT id FROM accounts WHERE id=? FOR UPDATE", id));
    var card = db.queryForMap("SELECT * FROM accounts WHERE id=?", cardAccount);
    var settlement = db.queryForMap("SELECT balance,status,currency_code FROM accounts WHERE id=?", cash);
    if (!"CARD".equals(card.get("ACCOUNT_TYPE")) || !"CREDIT".equals(card.get("PRODUCT_TYPE")) || !"ACTIVE".equals(card.get("PRODUCT_STATUS")) || !"ACTIVE".equals(card.get("STATUS")))
      throw new InvalidRequestException("Choose an active credit card.");
    if (!bill.currencyCode().equals(card.get("CURRENCY_CODE")) || !bill.currencyCode().equals(settlement.get("CURRENCY_CODE")) || !"ACTIVE".equals(settlement.get("STATUS")))
      throw new InvalidRequestException("The settlement account is unavailable for this currency.");
    if (amount.signum() <= 0 || amount.scale() > 2 || ((BigDecimal)card.get("CREDIT_LIMIT")).subtract((BigDecimal)card.get("BALANCE")).compareTo(amount) < 0)
      throw new InvalidRequestException("Insufficient available credit.");
    if (((BigDecimal)settlement.get("BALANCE")).compareTo(amount) < 0) throw new InvalidRequestException("Settlement funds are insufficient.");
    String tx = "TX-" + UUID.randomUUID();
    db.update("UPDATE accounts SET balance=balance+?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", amount, cardAccount);
    db.update("UPDATE accounts SET balance=balance-?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", amount, cash);
    db.update("INSERT INTO transactions(id,record_kind,user_id,transaction_type,source_account_id,amount,currency_code,status,completed_at,operation,parent_id,target_id,transaction_reference,merchant_name,category,payment_method,created_at) VALUES(?,'PAYMENT',?,'WITHDRAWAL',?,?,?,'SUCCESS',CURRENT_TIMESTAMP,'BILL_PAYMENT',?,?,?, ?,?,'CREDIT_CARD',CURRENT_TIMESTAMP)",
        tx, user.userId(), cardAccount, amount, bill.currencyCode(), bill.id(), card.get("PRODUCT_ID"), tx, bill.billerName(), bill.category());
    db.update("INSERT INTO journal_entries(transaction_id,entry_reference,entry_type,status,created_at) VALUES(?,?,'TRANSACTION','POSTED',CURRENT_TIMESTAMP)", tx, "J" + tx);
    db.update("INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at) SELECT id,?,'DEBIT',?,CURRENT_TIMESTAMP FROM journal_entries WHERE transaction_id=?", cardAccount, amount, tx);
    db.update("INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at) SELECT id,?,'CREDIT',?,CURRENT_TIMESTAMP FROM journal_entries WHERE transaction_id=?", cash, amount, tx);
    return tx;
  }
}
