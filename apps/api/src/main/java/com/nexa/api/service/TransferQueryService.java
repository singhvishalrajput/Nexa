package com.nexa.api.service;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.exep.ResourceNotFoundException;


import com.nexa.api.beans.BankingModels.Transfer;
import com.nexa.api.service.CurrentUserProvider;
import com.nexa.api.exep.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TransferQueryService {
  private final JdbcTemplate db;
  private final CurrentUserProvider user;

  public TransferQueryService(JdbcTemplate db, CurrentUserProvider user) {
    this.db = db;
    this.user = user;
  }

  public Transfer detail(String id) {
    return db
        .query(
            "SELECT"
                + " id,source_account_id,beneficiary_id,transfer_reference,amount,currency_code,status"
                + " FROM transfers WHERE user_id = ? AND id = ?",
            (r, n) ->
                new Transfer(
                    r.getString(1),
                    r.getString(2),
                    r.getString(3),
                    r.getString(4),
                    r.getBigDecimal(5).toPlainString(),
                    r.getString(6),
                    r.getString(7)),
            user.userId(),
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("The transfer was not found."));
  }
}
