package com.nexa.api.service;
import com.nexa.api.beans.BankingModels;
import com.nexa.api.exep.ResourceNotFoundException;


import com.nexa.api.beans.BankingModels.Beneficiary;
import com.nexa.api.service.CurrentUserProvider;
import com.nexa.api.exep.ResourceNotFoundException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
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

  private List<Beneficiary> query(String id) {
    return db.query(
        "SELECT id, display_name, bank_name, destination_account_masked, status FROM beneficiaries"
            + " WHERE user_id = ? AND (? IS NULL OR id = ?) ORDER BY display_name FETCH NEXT 100"
            + " ROWS ONLY",
        (r, n) ->
            new Beneficiary(
                r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)),
        user.userId(),
        id,
        id);
  }
}
