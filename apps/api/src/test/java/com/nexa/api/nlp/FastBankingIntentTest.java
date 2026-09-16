package com.nexa.api.nlp;
import com.nexa.api.beans.FastBankingIntent;
import com.nexa.api.beans.Intent;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FastBankingIntentTest {
  @Test
  void recognizesCompleteReadRequests() {
    Map.of(
            "balance",
            Intent.GET_BALANCE,
            "my accounts",
            Intent.GET_ACCOUNTS,
            "recent transactions",
            Intent.GET_RECENT_TRANSACTIONS,
            "bills",
            Intent.GET_BILLS,
            "credit cards",
            Intent.GET_CREDIT_CARDS,
            "cards",
            Intent.GET_CARDS,
            "beneficiaries",
            Intent.GET_BENEFICIARIES,
            "mandates",
            Intent.GET_MANDATES,
            "scheduled payments",
            Intent.GET_SCHEDULED_PAYMENTS,
            "loans",
            Intent.GET_LOANS)
        .forEach(
            (text, intent) -> {
              assertThat(FastBankingIntent.match(text)).isEqualTo(intent);
              assertThat(FastBankingIntent.match("Please show me " + text + "! "))
                  .isEqualTo(intent);
            });
  }
}
