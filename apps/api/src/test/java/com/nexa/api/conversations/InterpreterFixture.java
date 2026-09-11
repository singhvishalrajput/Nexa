package com.nexa.api.conversations;

import static org.mockito.Mockito.*;

import com.nexa.api.accounts.AccountQueryService;
import com.nexa.api.banking.*;
import com.nexa.api.beneficiaries.BeneficiaryQueryService;
import com.nexa.api.nlp.*;
import com.nexa.api.transactions.TransactionQueryService;
import com.nexa.api.transfers.TransferQueryService;

class InterpreterFixture {
  static ConversationInterpreter create(
      AccountQueryService accounts, TransactionQueryService transactions) {
    var embedding = new BasicEmbeddingProvider();
    var router =
        new BankingDomainRouter(
            accounts,
            transactions,
            mock(MandateQueryService.class),
            mock(BillQueryService.class),
            mock(CardQueryService.class),
            mock(BeneficiaryQueryService.class),
            mock(ScheduledPaymentQueryService.class),
            mock(LoanQueryService.class),
            mock(ActionPreparationService.class),
            mock(TransferQueryService.class));
    return new ConversationInterpreter(
        new BasicIntentClassifier(
            embedding, new InMemoryVectorIndex(new IntentRepository(), embedding), .70),
        new BasicEntityExtractor(java.time.Clock.systemUTC()),
        router,
        false);
  }
}
