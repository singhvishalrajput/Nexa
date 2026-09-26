package com.nexa.api.onboarding;

import com.nexa.api.exep.InvalidRequestException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Syntax checking and private identifier storage, NOT identity verification.
 * Aadhaar accepts/stores only its last four digits; a full Aadhaar number is
 * rejected, never truncated. PAN/passport use contextual authenticated encryption.
 * A separately derived keyed fingerprint protects low-entropy retry metadata from
 * offline guessing. No files, network calls or externally usable keys are created.
 *
 * This initial format has one externally managed key. Preserve that key and its ID
 * for existing records and retries; unknown IDs fail closed rather than silently
 * treating new key material as an old key. Key rotation needs an explicit migration.
 */
@Component
public final class IdentifierProtector {
  private static final byte[] HEADER = "NXID0001".getBytes(StandardCharsets.US_ASCII);
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int MAX_INPUT_CHARS = 32;
  private static final int MAX_ENVELOPE_CHARS = 512;
  private static final String INVALID_MESSAGE = "Identity details do not match the supported format.";
  private final SecureRandom random = new SecureRandom();
  private final SecretKeySpec encryptionKey;
  private final SecretKeySpec fingerprintKey;
  private final String keyId;

  public IdentifierProtector(
      @Value("${nexa.onboarding.identity.encryption-key:}") String base64Key,
      @Value("${nexa.onboarding.identity.key-id:}") String keyId) {
    SecretKeySpec encryption = null;
    SecretKeySpec fingerprint = null;
    byte[] master = null;
    byte[] aes = null;
    byte[] hmac = null;
    try {
      if (keyId != null && keyId.matches("[A-Za-z0-9_-]{1,80}")
          && base64Key != null && base64Key.length() == 44) {
        master = Base64.getDecoder().decode(base64Key);
        if (master.length == 32 && Base64.getEncoder().encodeToString(master).equals(base64Key)) {
          aes = derive(master, "nexa/identity/aes-256-gcm/v1");
          hmac = derive(master, "nexa/identity/request-fingerprint/v1");
          encryption = new SecretKeySpec(aes, "AES");
          fingerprint = new SecretKeySpec(hmac, "HmacSHA256");
        }
      }
    } catch (GeneralSecurityException | IllegalArgumentException ignored) {
      // Invalid/incomplete private configuration disables operations without leaking it.
    } finally {
      erase(master); erase(aes); erase(hmac);
    }
    this.encryptionKey = encryption;
    this.fingerprintKey = fingerprint;
    this.keyId = encryption == null || fingerprint == null ? null : keyId;
  }

  public boolean isReady() { return encryptionKey != null && fingerprintKey != null && keyId != null; }

  public String currentKeyId() { requireReady(); return keyId; }

  /** Format validation only: even a syntactically valid number needs in-person review. */
  public String normalize(String type, String input) {
    String canonicalType = canonicalType(type);
    String value = asciiTrimUpper(input, MAX_INPUT_CHARS);
    boolean valid = switch (canonicalType) {
      case "AADHAAR" -> value.matches("[0-9]{4}");
      case "PAN" -> value.matches("[A-Z]{3}P[A-Z][0-9]{4}[A-Z]");
      case "PASSPORT" -> value.matches("[A-Z][A-Z0-9]{7}") && value.matches(".*[0-9].*");
      default -> false;
    };
    if (!valid) throw invalid();
    return value;
  }

  public StoredIdentity protect(String applicationId, String customerUserId, String type, String input) {
    requireReady();
    requireContext(applicationId, customerUserId);
    String canonicalType = canonicalType(type);
    String normalized = normalize(canonicalType, input);
    String last4 = normalized.substring(normalized.length() - 4);
    if ("AADHAAR".equals(canonicalType)) return new StoredIdentity(canonicalType, null, null, last4);
    byte[] plaintext = normalized.getBytes(StandardCharsets.US_ASCII);
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(aad(applicationId, customerUserId, canonicalType, keyId));
      byte[] encrypted = cipher.doFinal(plaintext);
      byte[] envelope = ByteBuffer.allocate(HEADER.length + nonce.length + encrypted.length)
          .put(HEADER).put(nonce).put(encrypted).array();
      return new StoredIdentity(canonicalType, Base64.getEncoder().encodeToString(envelope), keyId, last4);
    } catch (GeneralSecurityException exception) {
      throw unavailable();
    } finally { erase(plaintext); }
  }

  /**
   * For authorised in-person review only; the calling service enforces role,
   * ownership and audit. Aadhaar returns ONLY its stored last four digits, never
   * reconstructs a full number. Its partial value has no ciphertext/authentication
   * tag; database access and the keyed application-event audit remain essential.
   */
  public String reveal(String applicationId, String customerUserId, String type,
      String ciphertext, String storedKeyId, String last4) {
    requireReady();
    byte[] plaintext = null;
    try {
      requireContext(applicationId, customerUserId);
      String canonicalType = canonicalType(type);
      if (!canonicalType.equals(type) || last4 == null || last4.length() != 4) throw unavailable();
      if ("AADHAAR".equals(canonicalType)) {
        if (ciphertext != null || storedKeyId != null || !last4.matches("[0-9]{4}")) throw unavailable();
        return last4;
      }
      requireKey(storedKeyId);
      if (storedKeyId == null || ciphertext == null || ciphertext.length() > MAX_ENVELOPE_CHARS)
        throw unavailable();
      byte[] envelope = Base64.getDecoder().decode(ciphertext);
      if (!Base64.getEncoder().encodeToString(envelope).equals(ciphertext)
          || envelope.length <= HEADER.length + NONCE_BYTES + TAG_BITS / 8
          || !MessageDigest.isEqual(HEADER, Arrays.copyOf(envelope, HEADER.length))) throw unavailable();
      byte[] nonce = Arrays.copyOfRange(envelope, HEADER.length, HEADER.length + NONCE_BYTES);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(aad(applicationId, customerUserId, canonicalType, storedKeyId));
      plaintext = cipher.doFinal(envelope, HEADER.length + NONCE_BYTES,
          envelope.length - HEADER.length - NONCE_BYTES);
      String value = new String(plaintext, StandardCharsets.US_ASCII);
      if (!normalize(canonicalType, value).equals(value)
          || !MessageDigest.isEqual(last4.getBytes(StandardCharsets.US_ASCII),
              value.substring(value.length() - 4).getBytes(StandardCharsets.US_ASCII))) throw unavailable();
      return value;
    } catch (GeneralSecurityException | IllegalArgumentException | InvalidRequestException exception) {
      // Never retain raw cipher/parser errors, causes or identifier material.
      throw unavailable();
    } finally { erase(plaintext); }
  }

  /** Domain and typed, length-prefixed parts prevent concatenation/null ambiguity. */
  public String fingerprint(String requestedKeyId, String domain, Object... parts) {
    requireReady();
    requireKey(requestedKeyId);
    if (domain == null || !domain.matches("[A-Za-z0-9_.:/-]{1,100}") || parts == null || parts.length > 64)
      throw invalid();
    byte[] payload = null;
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (DataOutputStream encoded = new DataOutputStream(output)) {
        field(encoded, "nexa/identity/request-fingerprint/v1");
        field(encoded, keyId);
        field(encoded, domain);
        encoded.writeInt(parts.length);
        for (Object part : parts) writePart(encoded, part);
      }
      payload = output.toByteArray();
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(fingerprintKey);
      return HexFormat.of().formatHex(mac.doFinal(payload));
    } catch (GeneralSecurityException | IOException exception) {
      throw unavailable();
    } finally { erase(payload); }
  }

  private static void writePart(DataOutputStream encoded, Object part) throws IOException {
    if (part == null) { field(encoded, "null"); return; }
    String tag;
    String text;
    if (part instanceof String value) { tag = "string"; text = value; }
    else if (part instanceof BigDecimal value) { tag = "decimal"; text = value.stripTrailingZeros().toPlainString(); }
    else if (part instanceof BigInteger || part instanceof Integer || part instanceof Long
        || part instanceof Short || part instanceof Byte) { tag = "integer"; text = part.toString(); }
    else if (part instanceof UUID) { tag = "uuid"; text = part.toString(); }
    else if (part instanceof Boolean) { tag = "boolean"; text = part.toString(); }
    else if (part instanceof TemporalAccessor) { tag = part.getClass().getName(); text = part.toString(); }
    else if (part instanceof Enum<?> value) { tag = value.getDeclaringClass().getName(); text = value.name(); }
    else throw invalid();
    if (text.length() > 4096) throw invalid();
    field(encoded, tag); field(encoded, text);
  }

  private static byte[] aad(String applicationId, String customerUserId, String type, String keyId) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (DataOutputStream encoded = new DataOutputStream(output)) {
        field(encoded, "nexa/identity/aes-256-gcm/v1");
        field(encoded, applicationId); field(encoded, customerUserId); field(encoded, type); field(encoded, keyId);
      }
      return output.toByteArray();
    } catch (IOException exception) { throw unavailable(); }
  }

  private static void field(DataOutputStream output, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    try { output.writeInt(bytes.length); output.write(bytes); }
    finally { erase(bytes); }
  }

  private static byte[] derive(byte[] master, String label) throws GeneralSecurityException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(master, "HmacSHA256"));
    return mac.doFinal(label.getBytes(StandardCharsets.US_ASCII));
  }

  private static String canonicalType(String type) {
    String value = asciiTrimUpper(type, 16);
    if (!value.equals("AADHAAR") && !value.equals("PAN") && !value.equals("PASSPORT")) throw invalid();
    return value;
  }

  private static String asciiTrimUpper(String input, int maxChars) {
    if (input == null || input.isEmpty() || input.length() > maxChars) throw invalid();
    for (int i = 0; i < input.length(); i++)
      if (input.charAt(i) < 32 || input.charAt(i) > 126) throw invalid();
    return input.trim().toUpperCase(Locale.ROOT);
  }

  private static void requireContext(String applicationId, String customerUserId) {
    if (applicationId == null || !applicationId.matches("[A-Za-z0-9_.:-]{1,128}")
        || customerUserId == null || !customerUserId.matches("[A-Za-z0-9_.:-]{1,128}")) throw invalid();
  }

  private void requireReady() { if (!isReady()) throw unavailable(); }
  private void requireKey(String requested) {
    if (requested != null && !keyId.equals(requested)) throw unavailable();
  }
  private static void erase(byte[] bytes) { if (bytes != null) Arrays.fill(bytes, (byte) 0); }
  private static InvalidRequestException invalid() { return new InvalidRequestException(INVALID_MESSAGE); }
  private static ProtectionUnavailableException unavailable() { return new ProtectionUnavailableException(); }

  /** Logging a result must not leak even a masked suffix or encrypted material. */
  public record StoredIdentity(String type, String ciphertext, String keyId, String last4) {
    @Override public String toString() { return "StoredIdentity[REDACTED]"; }
  }

  public static final class ProtectionUnavailableException extends RuntimeException {
    public ProtectionUnavailableException() { super("Private identity protection is unavailable."); }
  }
}
