package com.nexa.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatApplicationEntryTest {
  @ParameterizedTest
  @ValueSource(strings = {"open account", "Create an account!", "I want to open a savings account",
      "Please create a new current account", "Can you open a bank account please?", "account application form",
      "createaccount", "खाता खोलो", "नया बचत खाता खोलें"})
  void explicitAccountApplicationsOpenOnlyTheForm(String text) {
    var entry = ChatApplicationEntry.resolve(text);
    assertThat(entry).as(text).isNotNull();
    assertThat(entry.intent()).isEqualTo("OPEN_ACCOUNT");
    assertThat(entry.banking().version()).isEqualTo(1);
    assertThat(entry.banking().type()).isEqualTo("ACCOUNT_APPLICATION");
    assertThat(entry.banking().envelopeType()).isEqualTo("ACCOUNT_APPLICATION");
    assertThat(entry.banking().action()).isNull();
    assertThat(entry.reply()).contains("before submitting", "does not open an account or add money");
  }

  @ParameterizedTest
  @ValueSource(strings = {"create mandate", "Create a new mandate!", "Please set up a new direct debit", "Please set up a direct debit",
      "I want to create a mandate", "Can you setup a direct debit please?", "open direct debit",
      "direct debit authorization form", "new mandate application"})
  void explicitMandateApplicationsCannotActivateOrExecute(String text) {
    var entry = ChatApplicationEntry.resolve(text);
    assertThat(entry).as(text).isNotNull();
    assertThat(entry.intent()).isEqualTo("CREATE_MANDATE");
    assertThat(entry.banking().version()).isEqualTo(1);
    assertThat(entry.banking().type()).isEqualTo("MANDATE_APPLICATION");
    assertThat(entry.banking().envelopeType()).isEqualTo("MANDATE_APPLICATION");
    assertThat(entry.banking().action()).isNull();
    assertThat(entry.reply()).contains("pending mandate", "Activation is a separate step", "no money moves");
  }

  @ParameterizedTest
  @ValueSource(strings = {"show accounts", "show my account details", "account balance", "open account details",
      "view my account application", "my account application status", "How do I open an account?",
      "Can I open an account?", "don't create account", "open account tomorrow", "open account if eligible",
      "open account and transfer 500", "खाता नहीं खोलो", "show mandates", "direct debits", "mandate details",
      "cancel mandate", "activate mandate", "execute direct debit", "How do I create a mandate?",
      "Can I create a direct debit?", "do not create mandate", "create mandate tomorrow",
      "create mandate and pay bill", "create mandate if balance is enough", "create a loan account",
      "show loans", "repay loan", "confirm", "yes"})
  void readsCommandsAndGuardedRequestsDoNotOpenForms(String text) {
    assertThat(ChatApplicationEntry.resolve(text)).as(text).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"apply for loan", "I want to apply for a loan", "ऋण के लिए आवेदन"})
  void existingLoanApplicationEntryIsPreserved(String text) {
    assertThat(ChatApplicationEntry.resolve(text)).isEqualTo(LoanApplicationChatEntry.resolve(text));
  }
}
