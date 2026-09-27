package com.nexa.api.external;

import com.nexa.api.exep.InvalidRequestException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Domain-separated AES-GCM key; ciphertext is bound to its owner and immutable payee ID. */
@Component
public class ExternalAccountCipher {
  private final SecretKeySpec key;
  private final SecretKeySpec fingerprintKey;
  private final SecureRandom random = new SecureRandom();

  public ExternalAccountCipher(
      @Value("${nexa.payouts.account-key:${nexa.onboarding.identity.encryption-key:}}") String configured) {
    SecretKeySpec derived = null;
    SecretKeySpec derivedFingerprint = null;
    try {
      byte[] root = Base64.getDecoder().decode(configured == null ? "" : configured.trim());
      if (root.length == 32) {
        Mac derivation = Mac.getInstance("HmacSHA256");
        derivation.init(new SecretKeySpec(root, "HmacSHA256"));
        derived = new SecretKeySpec(derivation.doFinal("nexa/external-bank-account/v1".getBytes(StandardCharsets.UTF_8)), "AES");
        derivedFingerprint = new SecretKeySpec(derivation.doFinal("nexa/external-bank-fingerprint/v1".getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
        java.util.Arrays.fill(root, (byte) 0);
      }
    } catch (Exception ignored) { /* Missing configuration disables this capability only. */ }
    key = derived;
    fingerprintKey = derivedFingerprint;
  }

  public boolean available() { return key != null; }

  /** Owner-bound keyed fingerprints prevent guessing account numbers from a database copy. */
  public String fingerprint(String owner, String account, String ifsc) {
    requireKey();
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(fingerprintKey);
      return java.util.HexFormat.of().formatHex(mac.doFinal(
          (owner + ":" + ifsc + ":" + account).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception failure) {
      throw new InvalidRequestException("The external payee could not be verified securely. Try again later.");
    }
  }

  public String encrypt(String owner, String id, String account) {
    requireKey();
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      cipher.updateAAD(aad(owner, id));
      byte[] encrypted = cipher.doFinal(account.getBytes(StandardCharsets.UTF_8));
      return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
    } catch (Exception failure) {
      throw new InvalidRequestException("The external payee could not be stored securely. Try again later.");
    }
  }

  public String decrypt(String owner, String id, String value) {
    requireKey();
    try {
      if (value == null || !value.startsWith("v1:")) throw new IllegalArgumentException();
      byte[] bytes = Base64.getDecoder().decode(value.substring(3));
      if (bytes.length < 29) throw new IllegalArgumentException();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, java.util.Arrays.copyOf(bytes, 12)));
      cipher.updateAAD(aad(owner, id));
      return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
    } catch (Exception failure) {
      throw new InvalidRequestException("This payee's stored bank details could not be verified. Contact the administrator.");
    }
  }

  private static byte[] aad(String owner, String id) {
    return ("nexa/external-payee/v1:" + owner + ":" + id).getBytes(StandardCharsets.UTF_8);
  }

  private void requireKey() {
    if (key == null) throw new InvalidRequestException("Secure external-payee storage is not configured. Ask the administrator to configure the account encryption key.");
  }
}
