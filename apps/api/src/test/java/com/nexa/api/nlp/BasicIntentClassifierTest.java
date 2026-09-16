package com.nexa.api.nlp;
import com.nexa.api.beans.Intent;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.repository.IntentRepository;
import com.nexa.api.service.BasicEmbeddingProvider;
import com.nexa.api.service.BasicEntityExtractor;
import com.nexa.api.service.BasicIntentClassifier;
import com.nexa.api.service.EmbeddingProvider;
import com.nexa.api.service.InMemoryVectorIndex;
import com.nexa.api.service.IntentClassifier;
import com.nexa.api.service.VectorIndex;


import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BasicIntentClassifierTest {
  final EmbeddingProvider embedding = new BasicEmbeddingProvider();
  final VectorIndex index = new InMemoryVectorIndex(new IntentRepository(), embedding);
  final IntentClassifier classifier = new BasicIntentClassifier(embedding, index, .70);

  @Test
  void recognisesBankingVariations() {
    Map.ofEntries(
            Map.entry("show transactions", Intent.GET_RECENT_TRANSACTIONS),
            Map.entry("what did I spend recently", Intent.GET_RECENT_TRANSACTIONS),
            Map.entry("show my last payments", Intent.GET_RECENT_TRANSACTIONS),
            Map.entry("What mandates do I have?", Intent.GET_MANDATES),
            Map.entry("Which bills are due?", Intent.GET_BILLS),
            Map.entry("What's my credit card outstanding?", Intent.GET_CREDIT_CARDS),
            Map.entry("Show my credit card transactions", Intent.GET_CARD_TRANSACTIONS),
            Map.entry("What payments are coming up?", Intent.GET_SCHEDULED_PAYMENTS),
            Map.entry("What's my savings balance?", Intent.GET_BALANCE),
            Map.entry("Show my beneficiaries", Intent.GET_BENEFICIARIES),
            Map.entry("Show my loans", Intent.GET_LOANS),
            Map.entry("transaction details txn_demo_1", Intent.GET_TRANSACTION_DETAIL),
            Map.entry("pay bill bil_demo_1 from acc_demo_1", Intent.PAY_BILL))
        .forEach(
            (text, intent) ->
                assertThat(classifier.classify(text).intent()).as(text).isEqualTo(intent));
  }

  @Test
  void rejectsAmbiguousUnrelatedAndNegatedMessages() {
    for (String text :
        java.util.List.of(
            "show me what's coming up",
            "tell me a joke",
            "don't send money",
            "show balance and transfer money",
            "",
            "potato weather moon"))
      assertThat(classifier.classify(text).intent()).as(text).isEqualTo(Intent.UNKNOWN);
  }

  @Test
  void configurableThresholdAndProvider() {
    assertThatThrownBy(() -> new BasicIntentClassifier(embedding, index, 2))
        .isInstanceOf(IllegalArgumentException.class);
    IntentClassifier high = new BasicIntentClassifier(embedding, index, 1);
    assertThat(high.classify("show my recent unfamiliar transactions").intent())
        .isEqualTo(Intent.UNKNOWN);
    VectorIndex swapped =
        new InMemoryVectorIndex(new IntentRepository(), text -> new double[] {1, 2});
    assertThat(swapped.search(new double[] {1, 2}).get(0).score()).isCloseTo(1, within(.0001));
  }

  @Test
  void entityExtractionDoesNotRoundOrTruncatePaymentAmounts() {
    var extractor = new BasicEntityExtractor(java.time.Clock.systemUTC());
    assertThat(
            extractor.extract("transfer INR 10.999 to ben_demo_1", Intent.START_TRANSFER).amount())
        .isEqualByComparingTo("10.999");
    assertThatThrownBy(() -> extractor.extract("pay bill bil_1 bil_2", Intent.PAY_BILL))
        .isInstanceOf(com.nexa.api.exep.InvalidRequestException.class);
    assertThatThrownBy(() -> extractor.extract("transfer from acc_1 acc_2", Intent.START_TRANSFER))
        .isInstanceOf(com.nexa.api.exep.InvalidRequestException.class);
  }

  @Test
  void extractsReferencesMoneyAndCalendarRange() {
    var extractor =
        new BasicEntityExtractor(
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-09-10T00:00:00Z"), java.time.ZoneOffset.UTC));
    var e =
        extractor.extract(
            "transfer INR 5,000 to ben_demo_asha from acc_demo_current", Intent.START_TRANSFER);
    assertThat(e.amount()).isEqualByComparingTo("5000");
    assertThat(e.targetId()).isEqualTo("ben_demo_asha");
    assertThat(e.accountId()).isEqualTo("acc_demo_current");
    e = extractor.extract("show transactions from last month", Intent.GET_RECENT_TRANSACTIONS);
    assertThat(e.from()).isEqualTo(java.time.LocalDate.parse("2026-08-01"));
    assertThat(e.to()).isEqualTo(java.time.LocalDate.parse("2026-08-31"));
  }
}
