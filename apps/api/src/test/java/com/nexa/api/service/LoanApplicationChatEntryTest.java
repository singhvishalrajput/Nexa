package com.nexa.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoanApplicationChatEntryTest {
  @ParameterizedTest
  @ValueSource(strings = {
      "apply for loan", "Apply for a loan!", "applyforloan", "Please apply for a personal loan",
      "I want to apply for a loan", "I'd like to apply for a loan", "Can you apply for a loan please?",
      "request a new loan", "start my loan application", "I need a loan", "loan application form",
      "loan apply karo", "loan ke liye apply karo", "ऋण के लिए आवेदन", "मुझे लोन चाहिए"
  })
  void explicitApplicationsOpenOnlyTheExistingForm(String text) {
    var entry = LoanApplicationChatEntry.resolve(text);
    assertThat(entry).as(text).isNotNull();
    assertThat(entry.intent()).isEqualTo("APPLY_LOAN");
    assertThat(entry.banking().version()).isEqualTo(1);
    assertThat(entry.banking().type()).isEqualTo("LOAN_APPLICATION");
    assertThat(entry.banking().envelopeType()).isEqualTo("LOAN_APPLICATION");
    assertThat(entry.banking().action()).isNull();
    assertThat(entry.reply()).contains("three required monthly salary slips", "when you submit", "approval and your acceptance");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "show loans", "my loan application status", "view my loan application", "repay loan",
      "pay my EMI", "loan repayment", "How do I apply for a loan?", "Can I apply for a loan?",
      "What documents do I need to apply for a loan?", "don't apply for a loan",
      "do not apply for loan", "apply for loan tomorrow", "apply for loan if my salary arrives",
      "apply for loan and transfer 500", "apply for loan then repay", "loan apply nahi karo",
      "ऋण के लिए आवेदन नहीं", "show the loan application requirements", "loan", "yes", "confirm"
  })
  void readsRepaymentsInformationAndGuardedRequestsCannotOpenAnApplication(String text) {
    assertThat(LoanApplicationChatEntry.resolve(text)).as(text).isNull();
  }
}
