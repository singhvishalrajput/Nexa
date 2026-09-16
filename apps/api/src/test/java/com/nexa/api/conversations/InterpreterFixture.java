package com.nexa.api.conversations;
import com.nexa.api.repository.IntentRepository;
import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.ActionPreparationService;
import com.nexa.api.service.BankingDomainRouter;
import com.nexa.api.service.BasicEmbeddingProvider;
import com.nexa.api.service.BasicEntityExtractor;
import com.nexa.api.service.BasicIntentClassifier;
import com.nexa.api.service.BeneficiaryQueryService;
import com.nexa.api.service.BillQueryService;
import com.nexa.api.service.CardQueryService;
import com.nexa.api.service.ConversationInterpreter;
import com.nexa.api.service.InMemoryVectorIndex;
import com.nexa.api.service.LoanQueryService;
import com.nexa.api.service.MandateQueryService;
import com.nexa.api.service.ScheduledPaymentQueryService;
import com.nexa.api.service.TransactionQueryService;
import com.nexa.api.service.TransferQueryService;

import static org.mockito.Mockito.*;

import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.BeneficiaryQueryService;
import com.nexa.api.service.TransactionQueryService;
import com.nexa.api.service.TransferQueryService;

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
