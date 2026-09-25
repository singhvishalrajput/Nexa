package com.nexa.api.service;

import com.nexa.api.beans.BankingModels;
import com.nexa.api.beans.TransactionRequest;
import com.nexa.api.exep.InvalidRequestException;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Posts a bill settlement as a core payment, rather than retaining it as a provider simulation. */
@Service
public class BillPaymentService {
  private final ActionPreparationService preparation;
  private final BillQueryService bills;
  private final TransactionService transactions;
  private final JdbcTemplate db;
  @org.springframework.beans.factory.annotation.Autowired private CardService cards;
  @org.springframework.beans.factory.annotation.Autowired private AccountQueryService accounts;

  public BillPaymentService(
      ActionPreparationService preparation,
      BillQueryService bills,
      TransactionService transactions,
      JdbcTemplate db) {
    this.preparation = preparation;
    this.bills = bills;
    this.transactions = transactions;
    this.db = db;
  }

  @Transactional
  public String pay(String accountId, String billId, BigDecimal requestedAmount) {
    bills.detail(billId);
    db.queryForList("SELECT id FROM transactions WHERE id=? AND record_kind='BILL' FOR UPDATE", billId);
    var prepared = preparation.prepare("PAY_BILL", accountId, billId, requestedAmount);
    BankingModels.Bill bill = bills.detail(billId);
    if (!Set.of("UPCOMING", "DUE", "OVERDUE", "FAILED").contains(bill.status()))
      throw new InvalidRequestException("This bill is not payable.");

    var request = new TransactionRequest();
    request.setSourceAccountId(Long.valueOf(prepared.accountId()));
    request.setAmount(new BigDecimal(prepared.amount()));
    String reference = "CARD".equals(accounts.requireOwnedAccount(accountId).accountType())
        ? cards.chargeBill(accountId, bill, request.getAmount())
        : transactions.payBill(request, billId, bill.billerName(), bill.category()).getId();
    BigDecimal remaining = new BigDecimal(bill.amount()).subtract(request.getAmount());
    db.update(
        "UPDATE transactions SET amount=?,minimum_amount=CASE WHEN minimum_amount>? THEN minimum_amount-? ELSE 0 END,status=?, completed_at=CASE WHEN ?=0 THEN CURRENT_TIMESTAMP ELSE NULL END, updated_at=CURRENT_TIMESTAMP WHERE id=? AND record_kind='BILL'",
        remaining, request.getAmount(), request.getAmount(), remaining.signum() == 0 ? "PAID" : bill.status(), remaining, billId);
    return reference;
  }
}
