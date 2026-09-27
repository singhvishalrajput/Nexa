package com.nexa.api.external;

import static org.assertj.core.api.Assertions.*;
import com.nexa.api.exep.ConflictException;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ExternalPayeeServiceTest {
  private final String key = Base64.getEncoder().encodeToString(new byte[32]);
  private final AtomicReference<String> owner = new AtomicReference<>("owner-one");
  private JdbcTemplate db;
  private ExternalAccountCipher cipher;
  private ExternalPayeeService service;
  private SingleConnectionDataSource source;

  @BeforeEach void database() {
    source = new SingleConnectionDataSource("jdbc:h2:mem:external-payee-" + UUID.randomUUID() + ";MODE=Oracle", "sa", "", true);
    db = new JdbcTemplate(source);
    db.execute("CREATE TABLE customers(user_id VARCHAR2(26) PRIMARY KEY)");
    db.execute("CREATE TABLE accounts(account_number VARCHAR2(30))");
    db.update("INSERT INTO customers(user_id) VALUES('owner-one'),('owner-two')");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V28__external_bank_payees.sql")).execute(source);
    cipher = new ExternalAccountCipher(key);
    service = new ExternalPayeeService(db, owner::get, cipher);
  }

  @AfterEach void closeDatabase() { if (source != null) source.destroy(); }

  private ExternalPayeeService.SaveRequest request(String number, String confirmation, String ifsc) {
    return new ExternalPayeeService.SaveRequest(" Utility payee ", " Fixture Recipient ", " Fixture Bank ", number, confirmation, ifsc);
  }

  @Test void realAccountDetailsAreEncryptedAndPublicDtosAreMasked() {
    var result = service.create(request("123456789012AB12", "123456789012AB12", "test0123456"));
    assertThat(result.transferType()).isEqualTo("EXTERNAL_BANK");
    assertThat(result.accountNumberMasked()).isEqualTo("•••• AB12");
    assertThat(result.ifsc()).isEqualTo("TEST0123456");
    assertThat(result.toString()).doesNotContain("123456789012");
    String saved = db.queryForObject("SELECT account_encrypted FROM external_bank_payees WHERE id=?", String.class, result.id());
    assertThat(saved).startsWith("v1:").doesNotContain("123456789012AB12");
    var internal = service.requireOwned(result.id(), owner.get(), false);
    assertThat(internal.accountNumber()).isEqualTo("123456789012AB12");
    assertThat(internal.toString()).doesNotContain("123456789012AB12");
    assertThat(service.list()).hasSize(1);
  }

  @Test void ownershipAndDuplicateDestinationAreEnforced() {
    var saved = service.create(request("123456789012", "123456789012", "TEST0123456"));
    assertThatThrownBy(() -> service.create(request("123456789012", "123456789012", "test0123456")))
        .isInstanceOf(ConflictException.class);
    owner.set("owner-two");
    assertThat(service.list()).isEmpty();
    assertThatThrownBy(() -> service.detail(saved.id())).isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.requireOwned(saved.id(), owner.get(), false)).isInstanceOf(ResourceNotFoundException.class);
    assertThat(service.create(request("123456789012", "123456789012", "TEST0123456")).id()).isNotEqualTo(saved.id());
  }

  @Test void invalidAndUnconfirmedDetailsCannotBeSaved() {
    for (var invalid : java.util.List.of(
        request("12345678", "12345678", "TEST0123456"),
        request("1234567890123456789", "1234567890123456789", "TEST0123456"),
        request("123456789012", "123456789099", "TEST0123456"),
        request("123456789012", "123456789012", "TEST1123456"),
        request("1234567890'", "1234567890'", "TEST0123456")))
      assertThatThrownBy(() -> service.create(invalid)).isInstanceOf(InvalidRequestException.class);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM external_bank_payees", Integer.class)).isZero();
  }

  @Test void accountNumbersMayCoincideAcrossDifferentBanks() {
    db.update("INSERT INTO accounts(account_number) VALUES('123456789012')");
    var first = service.create(request("123456789012", "123456789012", "TEST0123456"));
    var second = service.create(request("123456789012", "123456789012", "OTHR0123456"));
    assertThat(first.id()).isNotEqualTo(second.id());
  }

  @Test void destinationFingerprintIsKeyedAndBoundToOwner() {
    String fingerprint = cipher.fingerprint("owner-one", "123456789", "TEST0123456");
    assertThat(fingerprint).isEqualTo(cipher.fingerprint("owner-one", "123456789", "TEST0123456"));
    assertThat(fingerprint).isNotEqualTo(cipher.fingerprint("owner-two", "123456789", "TEST0123456"));
    byte[] anotherKey = new byte[32];
    anotherKey[0] = 1;
    assertThat(fingerprint).isNotEqualTo(new ExternalAccountCipher(Base64.getEncoder().encodeToString(anotherKey))
        .fingerprint("owner-one", "123456789", "TEST0123456"));
    assertThat(fingerprint).isNotEqualTo(cipher.fingerprint("owner-one", "123456789", "OTHR0123456"));
  }

  @Test void copiedOrTamperedCiphertextCannotBeUsedForAnotherPayee() {
    String encrypted = cipher.encrypt("owner-one", "payee-one", "123456789012");
    assertThat(cipher.decrypt("owner-one", "payee-one", encrypted)).isEqualTo("123456789012");
    assertThat(cipher.encrypt("owner-one", "payee-one", "123456789012")).isNotEqualTo(encrypted);
    assertThatThrownBy(() -> cipher.decrypt("owner-two", "payee-one", encrypted)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> cipher.decrypt("owner-one", "payee-two", encrypted)).isInstanceOf(InvalidRequestException.class);
    byte[] tampered = Base64.getDecoder().decode(encrypted.substring(3));
    tampered[tampered.length - 1] ^= 1;
    assertThatThrownBy(() -> cipher.decrypt("owner-one", "payee-one", "v1:" + Base64.getEncoder().encodeToString(tampered)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test void missingKeyFailsClosedAndDoesNotStoreBankDetails() {
    var disabled = new ExternalPayeeService(db, owner::get, new ExternalAccountCipher(""));
    assertThatThrownBy(() -> disabled.create(request("123456789012", "123456789012", "TEST0123456")))
        .isInstanceOf(InvalidRequestException.class).hasMessageContaining("not configured");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM external_bank_payees", Integer.class)).isZero();
  }

  @Test void bankDetailTamperingIsDetectedBeforeProviderSubmission() {
    var saved = service.create(request("123456789012", "123456789012", "TEST0123456"));
    db.update("UPDATE external_bank_payees SET ifsc='FAKE0123456' WHERE id=?", saved.id());
    assertThatThrownBy(() -> service.requireOwned(saved.id(), owner.get(), false)).isInstanceOf(InvalidRequestException.class);
  }
}
