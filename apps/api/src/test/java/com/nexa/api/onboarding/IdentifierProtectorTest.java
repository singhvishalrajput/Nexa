package com.nexa.api.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.onboarding.IdentifierProtector.ProtectionUnavailableException;
import com.nexa.api.onboarding.IdentifierProtector.StoredIdentity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Synthetic format examples and inert test keys only; no environment, files, DB or network. */
class IdentifierProtectorTest {
  private static final String APP = "00000000-0000-4000-8000-000000000001";
  private static final String CUSTOMER = "00000000-0000-4000-8000-000000000002";
  private static final String PAN = "ABCPA1234Z";
  private static final String PASSPORT = "A1234567";
  private static final String KEY_ID = "identity-test-v1";
  private static final String KEY = testKey(1);
  private final IdentifierProtector protector = new IdentifierProtector(KEY, KEY_ID);

  @Test
  void completeCanonicalConfigurationIsReadyAndDoesNotNeedExternalServices() {
    assertThat(protector.isReady()).isTrue();
    assertThat(protector.currentKeyId()).isEqualTo(KEY_ID);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "not-base64", "AAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="})
  void incompleteOrNoncanonicalKeysFailClosed(String key) {
    assertUnavailableConfiguration(new IdentifierProtector(key, KEY_ID));
  }

  @Test
  void wrongKeyLengthWhitespaceAndNoncanonicalUnusedBitsFailClosed() {
    assertUnavailableConfiguration(new IdentifierProtector(Base64.getEncoder().encodeToString(new byte[31]), KEY_ID));
    assertUnavailableConfiguration(new IdentifierProtector(Base64.getEncoder().encodeToString(new byte[33]), KEY_ID));
    assertUnavailableConfiguration(new IdentifierProtector(" " + KEY, KEY_ID));
    assertUnavailableConfiguration(new IdentifierProtector(KEY + "\n", KEY_ID));
    // This decodes to 32 zero bytes but has noncanonical unused base64 bits.
    assertUnavailableConfiguration(new IdentifierProtector("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB=", KEY_ID));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "key/id", "key.id", "identity key", "clé", "key\n"})
  void missingOrUnsafeKeyIdFailsClosed(String keyId) {
    assertUnavailableConfiguration(new IdentifierProtector(KEY, keyId));
  }

  @Test
  void keyIdHasAnExplicitStorageBound() {
    assertThat(new IdentifierProtector(KEY, "x".repeat(80)).isReady()).isTrue();
    assertUnavailableConfiguration(new IdentifierProtector(KEY, "x".repeat(81)));
  }

  @ParameterizedTest
  @CsvSource({"PAN,ABCPA1234Z", "PASSPORT,A1234567", "PASSPORT,AB123456", "PASSPORT,ABCD123E", "PASSPORT,ABCDEFG1"})
  void encryptedIdentifiersRoundTripWithBoundedEnvelopeAndMaskedSuffix(String type, String input) {
    StoredIdentity stored = protector.protect(APP, CUSTOMER, type, input);
    assertThat(stored.type()).isEqualTo(type);
    assertThat(stored.keyId()).isEqualTo(KEY_ID);
    assertThat(stored.ciphertext()).hasSizeLessThanOrEqualTo(512).doesNotContain(input);
    assertThat(new String(Base64.getDecoder().decode(stored.ciphertext()), StandardCharsets.ISO_8859_1))
        .doesNotContain(input);
    assertThat(stored.last4()).isEqualTo(input.substring(input.length() - 4));
    assertThat(reveal(stored)).isEqualTo(input);
  }

  @Test
  void eachEncryptionUsesFreshRandomNonceWithoutChangingTheIdentifier() {
    Set<String> ciphertexts = new HashSet<>();
    for (int i = 0; i < 20; i++) {
      StoredIdentity stored = protector.protect(APP, CUSTOMER, "PAN", PAN);
      assertThat(ciphertexts.add(stored.ciphertext())).isTrue();
      assertThat(reveal(stored)).isEqualTo(PAN);
    }
  }

  @Test
  void asciiNormalizationDoesNotPretendToVerifyIdentity() {
    assertThat(protector.normalize(" pan ", " abcpa1234z ")).isEqualTo(PAN);
    assertThat(protector.normalize("passport", " ab123456 ")).isEqualTo("AB123456");
    assertThat(protector.normalize("AADHAAR", " 0123 ")).isEqualTo("0123");
    assertThat(protector.protect(APP, CUSTOMER, "pan", " abcpa1234z ").type()).isEqualTo("PAN");
    // These synthetic values pass syntax only; no register, service or identity evidence is consulted.
    assertThat(protector.normalize("PAN", "ZZZPZ9999Z")).isEqualTo("ZZZPZ9999Z");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"123456789012", "1234 5678 9012", "123", "12345", "12a4", "１２３４", "१२३४", "+1234", "1234\n", "12 34"})
  void aadhaarNeverAcceptsOrTruncatesAFullNumberOrNonAsciiDigits(String input) {
    assertThatThrownBy(() -> protector.protect(APP, CUSTOMER, "AADHAAR", input))
        .isInstanceOf(InvalidRequestException.class).hasMessage("Identity details do not match the supported format.");
  }

  @Test
  void aadhaarStoresOnlyTheFourDigitsAndNoCiphertextOrKeyIdentifier() {
    StoredIdentity stored = protector.protect(APP, CUSTOMER, "AADHAAR", "0123");
    assertThat(stored.type()).isEqualTo("AADHAAR");
    assertThat(stored.last4()).isEqualTo("0123");
    assertThat(stored.ciphertext()).isNull();
    assertThat(stored.keyId()).isNull();
    assertThat(reveal(stored)).isEqualTo("0123");
    assertThat(protector.fingerprint(stored.keyId(), "CREATE", "AADHAAR", stored.last4()))
        .matches("[a-f0-9]{64}");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ABCCA1234Z", "ABCPA123Z", "ABCPA12345Z", "ABCPA1234", "ABCPA12A4Z", "ABC P A1234Z", "ABCPA１２３４Z", "ABCKA1234Z", "ABCPA1234Z\n"})
  void rejectsNonPersonalPanMalformedValuesAndUnicodeBeforeCaseConversion(String input) {
    assertThatThrownBy(() -> protector.normalize("PAN", input)).isInstanceOf(InvalidRequestException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345678", "ABCDEFGH", "A123456", "A12345678", "A123 567", "A123456!", "Ａ1234567", "A१२३४५६७", "A1234567\n"})
  void rejectsMalformedPassportSyntax(String input) {
    assertThatThrownBy(() -> protector.normalize("PASSPORT", input)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void rejectsOversizedInputsAndUnsupportedTypesBeforeAnyEncryption() {
    assertThatThrownBy(() -> protector.normalize("PAN", " ".repeat(100_000) + PAN))
        .isInstanceOf(InvalidRequestException.class);
    for (String type : new String[]{null, "", "ADDRESS_PROOF", "ADHAAR", "ＰＡＮ", "PAN\n"}) {
      assertThatThrownBy(() -> protector.protect(APP, CUSTOMER, type, PAN))
          .isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  void everyEnvelopeByteIsAuthenticatedIncludingVersionNonceCiphertextAndTag() {
    StoredIdentity stored = protector.protect(APP, CUSTOMER, "PAN", PAN);
    byte[] original = Base64.getDecoder().decode(stored.ciphertext());
    for (int i = 0; i < original.length; i++) {
      byte[] changed = original.clone();
      changed[i] ^= 1;
      String encoded = Base64.getEncoder().encodeToString(changed);
      assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "PAN", encoded, KEY_ID, stored.last4()));
    }
  }

  @Test
  void rejectsSwappedApplicationCustomerTypeKeyIdAndSuffixMetadata() {
    StoredIdentity stored = protector.protect(APP, CUSTOMER, "PAN", PAN);
    assertUnavailable(() -> protector.reveal("other-app", CUSTOMER, "PAN", stored.ciphertext(), KEY_ID, stored.last4()));
    assertUnavailable(() -> protector.reveal(APP, "other-customer", "PAN", stored.ciphertext(), KEY_ID, stored.last4()));
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "PASSPORT", stored.ciphertext(), KEY_ID, stored.last4()));
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "AADHAAR", stored.ciphertext(), KEY_ID, stored.last4()));
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "PAN", stored.ciphertext(), "another-key", stored.last4()));
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "PAN", stored.ciphertext(), KEY_ID, "999Z"));
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "pan", stored.ciphertext(), KEY_ID, stored.last4()));
    assertUnavailable(() -> protector.reveal(null, CUSTOMER, "PAN", stored.ciphertext(), KEY_ID, stored.last4()));
    // Same bytes but relabelling the configured key cannot authenticate a relabelled envelope.
    IdentifierProtector renamed = new IdentifierProtector(KEY, "renamed-key");
    assertUnavailable(() -> renamed.reveal(APP, CUSTOMER, "PAN", stored.ciphertext(), "renamed-key", stored.last4()));
  }

  @Test
  void wrongKeyMaterialCannotRevealExistingCiphertextEvenWithSameKeyId() {
    StoredIdentity stored = protector.protect(APP, CUSTOMER, "PAN", PAN);
    IdentifierProtector other = new IdentifierProtector(testKey(2), KEY_ID);
    assertUnavailable(() -> other.reveal(APP, CUSTOMER, "PAN", stored.ciphertext(), KEY_ID, stored.last4()));
  }

  @Test
  void rejectsAbsentTruncatedNoncanonicalAndOversizedStoredEnvelopes() {
    StoredIdentity stored = protector.protect(APP, CUSTOMER, "PAN", PAN);
    for (String ciphertext : new String[]{null, "", "!notbase64!", "AA==", "A".repeat(513),
        stored.ciphertext().replace("=", ""), stored.ciphertext() + "\n"}) {
      assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "PAN", ciphertext, KEY_ID, stored.last4()));
    }
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "PAN", stored.ciphertext(), null, stored.last4()));
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "PAN", stored.ciphertext(), KEY_ID, null));
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "PAN", stored.ciphertext(), KEY_ID, "12345"));
  }

  @Test
  void rejectsCiphertextKeyMetadataAndFullNumberOnAadhaarRows() {
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "AADHAAR", "", null, "1234"));
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "AADHAAR", null, KEY_ID, "1234"));
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "AADHAAR", null, null, "123456789012"));
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "AADHAAR", null, null, "123Z"));
  }

  @Test
  void fingerprintIsStableKeyedPurposeSeparatedAndDoesNotDependOnEncryptionNonce() {
    String first = protector.fingerprint(null, "CREATE", APP, CUSTOMER, "PAN", PAN);
    assertThat(first).matches("[a-f0-9]{64}").doesNotContain(PAN);
    assertThat(protector.fingerprint(KEY_ID, "CREATE", APP, CUSTOMER, "PAN", PAN)).isEqualTo(first);
    protector.protect(APP, CUSTOMER, "PAN", PAN);
    assertThat(protector.fingerprint(KEY_ID, "CREATE", APP, CUSTOMER, "PAN", PAN)).isEqualTo(first);
    assertThat(protector.fingerprint(KEY_ID, "UPDATE", APP, CUSTOMER, "PAN", PAN)).isNotEqualTo(first);
    assertThat(protector.fingerprint(KEY_ID, "CREATE", APP, CUSTOMER, "PAN", "ABCPA1235Z")).isNotEqualTo(first);
    assertThat(new IdentifierProtector(testKey(2), KEY_ID).fingerprint(null, "CREATE", APP, CUSTOMER, "PAN", PAN))
        .isNotEqualTo(first);
    assertThat(new IdentifierProtector(KEY, "other-id").fingerprint(null, "CREATE", APP, CUSTOMER, "PAN", PAN))
        .isNotEqualTo(first);
    assertUnavailable(() -> protector.fingerprint("unknown-id", "CREATE", PAN));
  }

  @Test
  void lengthPrefixingAndTypeTagsSeparateAmbiguousOrNullRetryPayloads() {
    assertThat(protector.fingerprint(null, "CREATE", "ab", "c"))
        .isNotEqualTo(protector.fingerprint(null, "CREATE", "a", "bc"));
    assertThat(protector.fingerprint(null, "CREATE", (Object) null))
        .isNotEqualTo(protector.fingerprint(null, "CREATE", "null"));
    assertThat(protector.fingerprint(null, "CREATE", 1234))
        .isNotEqualTo(protector.fingerprint(null, "CREATE", "1234"));
    assertThat(protector.fingerprint(null, "CREATE", "a\u0000b", "c"))
        .isNotEqualTo(protector.fingerprint(null, "CREATE", "a", "b\u0000c"));
    assertThat(protector.fingerprint(null, "CREATE", new BigDecimal("1000.00")))
        .isEqualTo(protector.fingerprint(null, "CREATE", new BigDecimal("1000")));
    assertThat(protector.fingerprint(null, "CREATE", UUID.fromString(APP), LocalDate.of(1990, 1, 1), true))
        .matches("[a-f0-9]{64}");
  }

  @Test
  void boundsFingerprintInputsAndRejectsUncontrolledObjectSerialization() {
    for (String domain : new String[]{null, "", "bad purpose", "a".repeat(101)})
      assertThatThrownBy(() -> protector.fingerprint(null, domain, PAN)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> protector.fingerprint(null, "CREATE", new Object[65])).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> protector.fingerprint(null, "CREATE", (Object[]) null)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> protector.fingerprint(null, "CREATE", "x".repeat(4097))).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> protector.fingerprint(null, "CREATE", new Object())).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void resultsAndFailuresDoNotExposePlaintextKeyCiphertextOrSuffixThroughToStringOrCause() {
    StoredIdentity stored = protector.protect(APP, CUSTOMER, "PAN", PAN);
    assertThat(stored.toString()).isEqualTo("StoredIdentity[REDACTED]")
        .doesNotContain(PAN, KEY, stored.ciphertext(), stored.last4());
    assertThat(protector.protect(APP, CUSTOMER, "AADHAAR", "0123").toString()).doesNotContain("0123");
    assertThatThrownBy(() -> protector.normalize("PAN", "sensitive-input"))
        .hasMessage("Identity details do not match the supported format.").hasNoCause();
    assertUnavailable(() -> protector.reveal(APP, CUSTOMER, "PAN", "sensitive-input", KEY_ID, "1234"));
  }

  private String reveal(StoredIdentity stored) {
    return protector.reveal(APP, CUSTOMER, stored.type(), stored.ciphertext(), stored.keyId(), stored.last4());
  }

  private static String testKey(int seed) {
    byte[] bytes = new byte[32];
    for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (seed + i);
    return Base64.getEncoder().encodeToString(bytes);
  }

  private static void assertUnavailableConfiguration(IdentifierProtector candidate) {
    assertThat(candidate.isReady()).isFalse();
    assertUnavailable(candidate::currentKeyId);
    assertUnavailable(() -> candidate.protect(APP, CUSTOMER, "PAN", PAN));
    assertUnavailable(() -> candidate.protect(APP, CUSTOMER, "AADHAAR", "1234"));
    assertUnavailable(() -> candidate.reveal(APP, CUSTOMER, "AADHAAR", null, null, "1234"));
    assertUnavailable(() -> candidate.fingerprint(null, "CREATE", "1234"));
  }

  private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
    assertThatThrownBy(operation).isInstanceOf(ProtectionUnavailableException.class)
        .hasMessage("Private identity protection is unavailable.").hasNoCause();
  }
}
