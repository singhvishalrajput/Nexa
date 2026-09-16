package com.nexa.api.conversations;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountResponse;
import com.nexa.api.beans.BankingContent;
import com.nexa.api.beans.PageResponse;
import com.nexa.api.beans.TransactionResponse;
import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.ConversationInterpreter;
import com.nexa.api.service.TransactionQueryService;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import com.nexa.api.service.AccountQueryService;
import com.nexa.api.beans.AccountResponse;
import com.nexa.api.beans.PageResponse;
import com.nexa.api.service.TransactionQueryService;
import com.nexa.api.beans.TransactionResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BankingContentTest {
  private final AccountQueryService accounts = mock(AccountQueryService.class);
  private final TransactionQueryService transactions = mock(TransactionQueryService.class);
  private final ConversationInterpreter interpreter =
      InterpreterFixture.create(accounts, transactions);
  private final OffsetDateTime now = OffsetDateTime.parse("2026-09-10T09:15:00+05:30");
  private final AccountResponse account =
      new AccountResponse(
          "account-1",
          "Savings",
          "•••• 1234",
          "SAVINGS",
          "INR",
          new BigDecimal("42350.75"),
          new BigDecimal("42350.75"),
          "ACTIVE",
          now);

  @Test
  void balanceIsStructuredAndMoneyRetainsExactDecimals() throws Exception {
    when(accounts.currentAccounts()).thenReturn(List.of(account));
    var result = interpreter.interpret("What's my balance?");
    assertThat(result.reply()).isEqualTo("Here are your available balances.");
    assertThat(result.banking().accounts().get(0).availableBalance()).isEqualTo("42350.75");
    var json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
    var serialized = json.writeValueAsString(result.banking());
    assertThat(serialized).contains("\"availableBalance\":\"42350.75\"");
    var decoded = json.readValue(serialized, BankingContent.class);
    assertThat(decoded.accounts().get(0).availableBalance()).isEqualTo("42350.75");
    assertThat(decoded.accounts().get(0).accountNumberMasked()).isEqualTo("•••• 1234");
    assertThat(decoded.accounts().get(0).updatedAt().toInstant()).isEqualTo(now.toInstant());
  }

  @Test
  void transactionPreviewPreservesSignStatusAndTotalWithoutAParagraph() {
    when(accounts.currentAccounts()).thenReturn(List.of(account));
    var item =
        new TransactionResponse(
            "tx-1",
            "account-1",
            "REF-1",
            "PAYMENT",
            "Swiggy",
            "FOOD",
            new BigDecimal("-485.00"),
            "INR",
            "POSTED",
            now);
    when(transactions.transactions("account-1", null, null, null, 0, 5))
        .thenReturn(new PageResponse<>(List.of(item), 0, 5, 18, 4));
    var result = interpreter.interpret("Show my recent transactions");
    assertThat(result.reply()).isEqualTo("Here are your latest transactions.");
    assertThat(result.banking().type()).isEqualTo("TRANSACTIONS");
    assertThat(result.banking().totalElements()).isEqualTo(18);
    assertThat(result.banking().transactions().get(0).amount()).isEqualTo("-485.00");
    assertThat(result.banking().transactions().get(0).status()).isEqualTo("POSTED");
    assertThat(result.banking().account().accountNumberMasked()).isEqualTo("•••• 1234");
  }

  @Test
  void unsupportedRequestDoesNotInventFinancialData() {
    assertThat(interpreter.interpret("Show my mandates").banking().type()).isEqualTo("MANDATES");
    verifyNoInteractions(accounts, transactions);
  }
}
