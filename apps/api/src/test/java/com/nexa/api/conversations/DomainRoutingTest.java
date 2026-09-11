package com.nexa.api.conversations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexa.api.accounts.AccountQueryService;
import com.nexa.api.banking.*;
import com.nexa.api.beneficiaries.BeneficiaryQueryService;
import com.nexa.api.nlp.*;
import com.nexa.api.transactions.TransactionQueryService;
import com.nexa.api.transfers.TransferQueryService;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;

class DomainRoutingTest {
  final MandateQueryService mandates = mock(MandateQueryService.class);
  final BillQueryService bills = mock(BillQueryService.class);
  final CardQueryService cards = mock(CardQueryService.class);
  final BeneficiaryQueryService beneficiaries = mock(BeneficiaryQueryService.class);
  final ScheduledPaymentQueryService scheduled = mock(ScheduledPaymentQueryService.class);
  final LoanQueryService loans = mock(LoanQueryService.class);
  final ActionPreparationService actions = mock(ActionPreparationService.class);
  final BankingDomainRouter router =
      new BankingDomainRouter(
          mock(AccountQueryService.class),
          mock(TransactionQueryService.class),
          mandates,
          bills,
          cards,
          beneficiaries,
          scheduled,
          loans,
          actions,
          mock(TransferQueryService.class));

  ConversationInterpreter interpreter(boolean debug) {
    var embedding = new BasicEmbeddingProvider();
    return new ConversationInterpreter(
        new BasicIntentClassifier(
            embedding, new InMemoryVectorIndex(new IntentRepository(), embedding), .7),
        new BasicEntityExtractor(Clock.systemUTC()),
        router,
        debug);
  }

  @Test
  void chatUsesStableTypesAndSharedServices() {
    Map.of(
            "show mandates",
            "MANDATE_LIST",
            "show bills",
            "BILL_LIST",
            "show my credit card",
            "CREDIT_CARD_LIST",
            "show beneficiaries",
            "BENEFICIARY_LIST",
            "show scheduled payments",
            "SCHEDULED_PAYMENT_LIST",
            "show loans",
            "LOAN_SUMMARY")
        .forEach(
            (text, type) ->
                assertThat(interpreter(false).interpret(text).banking().envelopeType())
                    .as(text)
                    .isEqualTo(type));
    verify(mandates).list(null, 0, 30);
    verify(bills).list(null, 0, 30);
    verify(cards).creditCards(null, 0, 30);
    verify(beneficiaries).list();
    verify(scheduled).list(null, 0, 30);
    verify(loans).list(null, 0, 30);
  }

  @Test
  void weakQueriesNeverCallBusinessServicesAndMetadataIsOptional() {
    var result = interpreter(false).interpret("tell me a joke");
    assertThat(result.intent()).isEqualTo("UNKNOWN");
    assertThat(result.banking().errorCode()).isEqualTo("LOW_CONFIDENCE_INTENT");
    assertThat(result.banking().meta()).isNull();
    assertThat(interpreter(true).interpret("show mandates").banking().meta())
        .containsKeys("intent", "confidence");
    verifyNoInteractions(actions, bills, cards, beneficiaries, scheduled, loans);
  }

  @Test
  void missingActionEntitiesRequireClarificationNotExecution() {
    var result = interpreter(false).interpret("pay bill");
    assertThat(result.banking().envelopeType()).isEqualTo("ACTION_REQUIRED");
    verifyNoInteractions(actions);
  }

  @Test
  void dueBillsApplyTheExtractedStatusFilter() {
    interpreter(false).interpret("Which bills are due?");
    verify(bills).list("DUE", 0, 30);
  }

  @Test
  void savedTurnExposesEnvelopeWithoutBreakingLegacyFields() {
    var result = interpreter(false).interpret("show mandates");
    var turn =
        new ConversationService.Turn(
            "1",
            "client",
            "TEXT",
            result.intent(),
            "show mandates",
            result.reply(),
            java.time.OffsetDateTime.now(),
            result.banking());
    assertThat(turn.response().type()).isEqualTo("MANDATE_LIST");
    assertThat(turn.response().data()).isSameAs(turn.banking());
    assertThat(turn.response().message()).isEqualTo(turn.assistantText());
  }
}
