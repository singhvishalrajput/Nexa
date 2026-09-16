package com.nexa.api.nlp;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BankingLanguageTest {
  @Test
  void recognizesEnglishHindiAndMixedRequests() {
    for (String text :
        List.of(
            "pay electricity bill",
            "bill pay karo",
            "bijli ka bill pay karo",
            "बिजली का बिल भुगतान करो"))
      assertThat(BankingLanguage.operation(text)).as(text).isEqualTo("PAY_BILL");
    for (String text : List.of("balance batao", "मेरे खाते का बैलेंस बताइए", "show my balance"))
      assertThat(BankingLanguage.intent(text)).as(text).isEqualTo(Intent.GET_BALANCE);
    for (String text : List.of("paisa bhejo", "पैसे भेजो", "send money", "Rahul ko 500 bhejo"))
      assertThat(BankingLanguage.operation(text)).as(text).isEqualTo("START_TRANSFER");
    assertThat(BankingLanguage.operation("show my bill payments")).isNull();
  }

  @Test
  void followupsAreBoundedAndNegationCannotBecomeConfirmation() {
    for (String text :
        List.of("yes", "do it", "pay karo", "haan", "हाँ कर दो", "confirm", "show it"))
      assertThat(BankingLanguage.continuation(text)).as(text).isTrue();
    for (String text : List.of("nahi cancel karo", "नहीं रद्द करो", "cancel", "stop"))
      assertThat(BankingLanguage.cancel(text)).as(text).isTrue();
    for (String text :
        List.of(
            "don't pay the bill",
            "kal paisa bhejo",
            "bill pay nahi karo",
            "send 500 if salary arrives",
            "cancel mat karo")) {
      assertThat(BankingLanguage.operation(text)).as(text).isNull();
      assertThat(BankingLanguage.continuation(text)).as(text).isFalse();
      assertThat(BankingLanguage.cancel(text)).as(text).isFalse();
    }
  }
}
