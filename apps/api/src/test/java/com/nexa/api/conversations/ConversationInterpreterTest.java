package com.nexa.api.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.nexa.api.accounts.AccountQueryService;
import com.nexa.api.transactions.TransactionQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationInterpreterTest {
  private final AccountQueryService accounts = mock(AccountQueryService.class);
  private final TransactionQueryService transactions = mock(TransactionQueryService.class);
  private final ConversationInterpreter interpreter =
      InterpreterFixture.create(accounts, transactions);

  @Test
  void understandsBalanceWithoutKeepingSpokenWording() {
    when(accounts.currentAccounts()).thenReturn(List.of());
    var result = interpreter.interpret("Please tell me my balance!");
    assertThat(result.intent()).isEqualTo("GET_BALANCE");
    assertThat(result.essence()).isEqualTo("get balance.");
    assertThat(result.reply()).contains("do not have a matching Nexa account");
  }

  @Test
  void preservesTransferAmountAndRecipientButDoesNotClaimExecution() {
    var result = interpreter.interpret("Please send ₹5,000 to Rahul");
    assertThat(result.essence()).isEqualTo("Request to transfer INR 5000 to rahul.");
    assertThat(result.reply()).contains("No money has moved");
    verifyNoInteractions(accounts, transactions);
  }

  @Test
  void doesNotDropNegationConditionsOrTiming() {
    for (String text :
        List.of(
            "Don't send ₹500 to Rahul",
            "Send ₹500 to Rahul tomorrow",
            "Send ₹500 to Rahul if my salary arrives",
            "Show my balance and transfer it to Rahul",
            "Send ₹500 to Rahul and Priya")) {
      assertThat(interpreter.interpret(text).intent()).as(text).isEqualTo("UNKNOWN");
    }
    verifyNoInteractions(accounts, transactions);
  }

  @Test
  void unknownSpeechDoesNotLeakIntoStoredEssence() {
    var result = interpreter.interpret("My private code is 123456, could you do that thing?");
    assertThat(result.essence()).isEqualTo("Unclear request; clarification needed.");
    assertThat(result.reply()).doesNotContain("123456");
  }
}
