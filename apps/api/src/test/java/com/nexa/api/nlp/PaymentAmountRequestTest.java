package com.nexa.api.nlp;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexa.api.beans.BankingLanguage;
import com.nexa.api.service.PaymentAmountRequest;
import org.junit.jupiter.api.Test;

class PaymentAmountRequestTest {
  @Test
  void parsesCommonPercentagesWithoutTreatingThemAsMoney() {
    for (String phrase : new String[] {"half", "50%", "50 %", "50 percent", "50 per cent"})
      assertThat(PaymentAmountRequest.parse("pay " + phrase + " of my bill", "PAY_BILL").percentage())
          .isEqualByComparingTo("50");
    assertThat(PaymentAmountRequest.parse("pay a quarter of my bill", "PAY_BILL").percentage())
        .isEqualByComparingTo("25");
    assertThat(PaymentAmountRequest.parse("pay 12.5% of my bill", "PAY_BILL").percentage())
        .isEqualByComparingTo("12.5");
    for (String value : new String[] {"0%", "-1%", "100.01%", "wrong%", "1.2.3%", "20% or 30%"})
      assertThat(PaymentAmountRequest.parse("pay " + value + " of my bill", "PAY_BILL").mode())
          .isEqualTo("INVALID_PERCENT");
    assertThat(PaymentAmountRequest.parse("pay 50 rupees", "PAY_BILL")).isNull();
    assertThat(PaymentAmountRequest.parse("transfer half", "START_TRANSFER")).isNull();
    assertThat(PaymentAmountRequest.parse("what is my minimum due", "PAY_CARD")).isNull();
    assertThat(PaymentAmountRequest.parse("show my full outstanding", "PAY_CARD")).isNull();
  }

  @Test
  void repaymentRoutingDoesNotTurnInformationalOrGuardedRequestsIntoActions() {
    for (String phrase : new String[] {"Pay my full credit-card outstanding", "Clear my card bill",
        "Pay my full credit card balance", "Pay my credit card bill using another credit card"})
      assertThat(BankingLanguage.operation(phrase)).isEqualTo("PAY_CARD");
    assertThat(BankingLanguage.operation("pay half my electricity bill using my credit card"))
        .isEqualTo("PAY_BILL");
    for (String phrase : new String[] {"show my credit card outstanding", "how do I clear my card bill",
        "don't clear my card bill", "pay my credit card bill tomorrow",
        "pay my credit card bill and electricity bill"})
      assertThat(BankingLanguage.operation(phrase)).isNull();
  }
}
