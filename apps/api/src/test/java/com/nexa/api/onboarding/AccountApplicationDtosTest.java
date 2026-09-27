package com.nexa.api.onboarding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexa.api.onboarding.AccountApplicationDtos.ActionRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.CashReceiptRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.CreateRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.IdentityRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.DetailsRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.DocumentUploadRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.ReasonRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.RefundRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.ReviewDocumentRequest;
import com.nexa.api.onboarding.AccountApplicationDtos.ReviewRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class AccountApplicationDtosTest {
  private static final String KEY = "86436341-547b-4faa-a38a-bfd2bde4c46a";
  private static final String PREFIX = "\"requestKey\":\"" + KEY + "\",\"expectedVersion\":0";
  private final JsonMapper mapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  private static String create(String amount) {
    return "{\"requestKey\":\"" + KEY + "\",\"accountType\":\"SAVINGS\","
        + "\"currencyCode\":\"INR\",\"dateOfBirth\":\"1990-01-01\",\"openingAmount\":"
        + amount + ",\"identityType\":\"PAN\",\"identityNumber\":\"ABCPD1234E\",\"consentVersion\":\"in-person-identity-v1\",\"consentAccepted\":true}";
  }

  @ParameterizedTest
  @ValueSource(strings = {"1000", "1000.00", "1500.5", "10000000.00"})
  void acceptsExactMoneyBoundaries(String amount) {
    assertTrue(validator.validate(mapper.readValue(create("\"" + amount + "\""), CreateRequest.class)).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"999.99", "10000000.01", "-1000", "1e3", "NaN", "Infinity",
      "1,000", " 1000", "1000 ", "1000.000", "01000", "+1000", ""})
  void rejectsUnsupportedMoneyText(String amount) {
    assertFalse(validator.validate(mapper.readValue(create("\"" + amount + "\""), CreateRequest.class)).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1000", "1000.00", "1e3", "true", "[]", "{}"})
  void rejectsMoneyTokenCoercion(String amountToken) {
    assertThrows(RuntimeException.class, () -> mapper.readValue(create(amountToken), CreateRequest.class));
  }

  @Test
  void requiresConsentAndAllCreateFields() {
    assertFalse(validator.validate(mapper.readValue("{}", CreateRequest.class)).isEmpty());
    assertFalse(validator.validate(mapper.readValue(create("\"1000\"").replace(
        "\"consentAccepted\":true", "\"consentAccepted\":false"), CreateRequest.class)).isEmpty());
    assertFalse(validator.validate(mapper.readValue(create("null"), CreateRequest.class)).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"true\"", "1", "{}", "[]"})
  void rejectsConsentBooleanCoercion(String token) {
    assertThrows(RuntimeException.class, () -> mapper.readValue(create("\"1000\"").replace(
        "\"consentAccepted\":true", "\"consentAccepted\":" + token), CreateRequest.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.5", "1e1", "\"1\"", "true", "9223372036854775808"})
  void rejectsNonIntegerOrOverflowingVersions(String version) {
    assertThrows(RuntimeException.class, () -> mapper.readValue(
        "{" + PREFIX.replace("\"expectedVersion\":0", "\"expectedVersion\":" + version) + "}",
        ActionRequest.class));
  }

  @Test
  void rejectsMissingAndNegativeVersions() {
    assertFalse(validator.validate(mapper.readValue("{\"requestKey\":\"" + KEY + "\"}", ActionRequest.class)).isEmpty());
    assertFalse(validator.validate(mapper.readValue("{" + PREFIX.replace(":0", ":-1") + "}", ActionRequest.class)).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1990-02-30", "0000-01-01", "1990-1-1", "1990-01-01T00:00:00Z"})
  void rejectsInvalidDateFormats(String date) {
    assertThrows(RuntimeException.class, () -> mapper.readValue(
        create("\"1000\"").replace("1990-01-01", date), CreateRequest.class));
  }

  @Test
  void rejectsUnknownFieldsForEveryRequestDespiteGlobalIgnoreUnknown() {
    assertUnknown(create("\"1000\""), CreateRequest.class);
    assertUnknown("{" + PREFIX + "}", ActionRequest.class);
    assertUnknown("{" + PREFIX + ",\"documentType\":\"PAN\"}", DocumentUploadRequest.class);
    assertUnknown("{" + PREFIX + ",\"decision\":\"ACCEPTED\",\"reason\":\"Reviewed copy\"}", ReviewDocumentRequest.class);
    assertUnknown("{" + PREFIX + ",\"decision\":\"APPROVED\",\"reason\":\"Reviewed copy\"}", ReviewRequest.class);
    assertUnknown("{" + PREFIX + ",\"cashReceivedConfirmed\":true}", CashReceiptRequest.class);
    assertUnknown("{" + PREFIX + ",\"reason\":\"Customer requested cancellation\"}", ReasonRequest.class);
    assertUnknown("{" + PREFIX + ",\"cashReturnedConfirmed\":true}", RefundRequest.class);
  }

  @Test
  void identityRequestsRejectNumericTokensAndRedactRecordStrings() {
    assertThrows(RuntimeException.class,()->mapper.readValue("{"+PREFIX+",\"identityType\":\"AADHAAR\",\"identityNumber\":1234}",IdentityRequest.class));
    var request=mapper.readValue("{"+PREFIX+",\"identityType\":\"PAN\",\"identityNumber\":\"ABCPD1234E\"}",IdentityRequest.class);
    assertTrue(validator.validate(request).isEmpty());
    assertFalse(request.toString().contains("ABCPD1234E"));
    assertFalse(mapper.readValue(create("\"1000\""),CreateRequest.class).toString().contains("ABCPD1234E"));
  }

  @Test
  void detailCorrectionsUseStrictTypesAndKeepPrivateFieldsOutOfRecordText() {
    String body="{"+PREFIX+",\"phoneNumber\":\"9876543210\",\"dateOfBirth\":\"1990-01-01\",\"openingAmount\":\"2500.37\"}";
    DetailsRequest retained=mapper.readValue(body,DetailsRequest.class);
    assertTrue(validator.validate(retained).isEmpty());
    assertTrue(retained.identityType()==null && retained.identityNumber()==null);
    assertThrows(RuntimeException.class,()->mapper.readValue(body.replace("\"9876543210\"","9876543210"),DetailsRequest.class));
    assertThrows(RuntimeException.class,()->mapper.readValue(body.replace("\"2500.37\"","2500.37"),DetailsRequest.class));
    assertUnknown(body,DetailsRequest.class);
    String changed=body.substring(0,body.length()-1)+",\"identityType\":\"PAN\",\"identityNumber\":\"ABCPD1234E\"}";
    DetailsRequest replacement=mapper.readValue(changed,DetailsRequest.class);
    assertTrue(validator.validate(replacement).isEmpty());
    assertFalse(replacement.toString().contains("9876543210"));
    assertFalse(replacement.toString().contains("ABCPD1234E"));
    assertFalse(validator.validate(mapper.readValue(body.replace("\"9876543210\"","null"),DetailsRequest.class)).isEmpty());
  }

  private void assertUnknown(String json, Class<?> type) {
    String withExtra = json.substring(0, json.length() - 1) + ",\"verified\":true}";
    assertThrows(RuntimeException.class, () -> mapper.readValue(withExtra, type), type.getSimpleName());
  }

  @Test
  void rejectsNumericDocumentEnumsAndNonCanonicalUuids() {
    assertThrows(RuntimeException.class, () -> mapper.readValue(
        "{" + PREFIX + ",\"documentType\":0}", DocumentUploadRequest.class));
    assertThrows(RuntimeException.class, () -> mapper.readValue(
        "{" + PREFIX.replace(KEY, "1-1-1-1-1") + "}", ActionRequest.class));
  }

  @Test
  void requiresExplicitCashReceiptAndRefundAcknowledgements() {
    String cash = "{" + PREFIX + ",\"cashReceivedConfirmed\":false}";
    assertFalse(validator.validate(mapper.readValue(cash, CashReceiptRequest.class)).isEmpty());
    assertFalse(validator.validate(mapper.readValue("{" + PREFIX + ",\"cashReturnedConfirmed\":false}", RefundRequest.class)).isEmpty());
  }

  @Test
  void rejectsCallerSuppliedReceiptNumbersAndAmounts() {
    String cash = "{" + PREFIX + ",\"cashReceivedConfirmed\":true";
    assertTrue(validator.validate(mapper.readValue(cash + "}", CashReceiptRequest.class)).isEmpty());
    assertThrows(RuntimeException.class, () -> mapper.readValue(cash + ",\"amount\":\"1000\"}", CashReceiptRequest.class));
    assertThrows(RuntimeException.class, () -> mapper.readValue(cash + ",\"receiptNumber\":\"CUSTOM-1\"}", CashReceiptRequest.class));
  }

  @Test
  void boundsReviewReasons() {
    String reason = "{" + PREFIX + ",\"reason\":\"" + "x".repeat(501) + "\"}";
    assertFalse(validator.validate(mapper.readValue(reason, ReasonRequest.class)).isEmpty());
    assertFalse(validator.validate(mapper.readValue("{" + PREFIX + ",\"reason\":\"   \"}", ReasonRequest.class)).isEmpty());
  }
}