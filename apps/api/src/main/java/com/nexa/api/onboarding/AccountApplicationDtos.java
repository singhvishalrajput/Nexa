package com.nexa.api.onboarding;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.nexa.api.exep.InvalidRequestException;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

/** Customer input only: actor identities, approval, accounts and posting IDs are server-owned. */
public final class AccountApplicationDtos {
  private AccountApplicationDtos() {}

  public static final String MONEY = "(?:0|[1-9][0-9]{0,7})(?:\\.[0-9]{1,2})?";
  public static final String UUID_TEXT =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  /** A local catch-all overrides Jackson's global unknown-field tolerance for these requests. */
  public interface StrictRequest {
    @JsonAnySetter
    default void rejectUnknownField(String name, Object ignored) {
      throw new InvalidRequestException("Unexpected account-application request field.");
    }
  }

  public record CreateRequest(
      @NotNull @JsonDeserialize(using = StrictUuid.class) UUID requestKey,
      @NotBlank @Pattern(regexp = "SAVINGS")
          @JsonDeserialize(using = StrictString.class) String accountType,
      @NotBlank @Pattern(regexp = "INR")
          @JsonDeserialize(using = StrictString.class) String currencyCode,
      @NotNull @JsonDeserialize(using = StrictDate.class) LocalDate dateOfBirth,
      @NotBlank @Pattern(regexp = MONEY) @DecimalMin("1000.00") @DecimalMax("10000000.00")
          @JsonDeserialize(using = StrictString.class) String openingAmount,
      @NotBlank @Pattern(regexp = "AADHAAR|PAN|PASSPORT")
          @JsonDeserialize(using = StrictString.class) String identityType,
      @NotBlank @Size(max = 10)
          @JsonDeserialize(using = StrictString.class) String identityNumber,
      @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._-]+")
          @JsonDeserialize(using = StrictString.class) String consentVersion,
      @NotNull @AssertTrue @JsonDeserialize(using = StrictBoolean.class)
          Boolean consentAccepted) implements StrictRequest {
    @Override public String toString() { return "CreateRequest[private applicant details redacted]"; }
  }

  public record IdentityRequest(
      @NotNull @JsonDeserialize(using = StrictUuid.class) UUID requestKey,
      @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLong.class) Long expectedVersion,
      @NotBlank @Pattern(regexp = "AADHAAR|PAN|PASSPORT")
          @JsonDeserialize(using = StrictString.class) String identityType,
      @NotBlank @Size(max = 10)
          @JsonDeserialize(using = StrictString.class) String identityNumber) implements StrictRequest {
    @Override public String toString() { return "IdentityRequest[private identity details redacted]"; }
  }

  public record ActionRequest(
      @NotNull @JsonDeserialize(using = StrictUuid.class) UUID requestKey,
      @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLong.class)
          Long expectedVersion) implements StrictRequest {}

  public enum DocumentType { AADHAAR, PAN, ADDRESS_PROOF }

  public record DocumentUploadRequest(
      @NotNull @JsonDeserialize(using = StrictUuid.class) UUID requestKey,
      @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLong.class) Long expectedVersion,
      @NotNull @JsonDeserialize(using = StrictDocumentType.class)
          DocumentType documentType) implements StrictRequest {}

  public record ReviewDocumentRequest(
      @NotNull @JsonDeserialize(using = StrictUuid.class) UUID requestKey,
      @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLong.class) Long expectedVersion,
      @NotBlank @Pattern(regexp = "ACCEPTED|REJECTED")
          @JsonDeserialize(using = StrictString.class) String decision,
      @NotBlank @Size(max = 500) @Pattern(regexp = "[^\\p{Cc}]*")
          @JsonDeserialize(using = StrictString.class) String reason) implements StrictRequest {}

  public record ReviewRequest(
      @NotNull @JsonDeserialize(using = StrictUuid.class) UUID requestKey,
      @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLong.class) Long expectedVersion,
      @NotBlank @Pattern(regexp = "APPROVED|REJECTED|CHANGES_REQUESTED")
          @JsonDeserialize(using = StrictString.class) String decision,
      @NotBlank @Size(max = 500) @Pattern(regexp = "[^\\p{Cc}]*")
          @JsonDeserialize(using = StrictString.class) String reason,
      @NotNull @JsonDeserialize(using = StrictBoolean.class) Boolean inPersonChecked) implements StrictRequest {}

  public record CashReceiptRequest(
      @NotNull @JsonDeserialize(using = StrictUuid.class) UUID requestKey,
      @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLong.class) Long expectedVersion,
      @NotNull @AssertTrue @JsonDeserialize(using = StrictBoolean.class)
          Boolean cashReceivedConfirmed) implements StrictRequest {}

  public record ReasonRequest(
      @NotNull @JsonDeserialize(using = StrictUuid.class) UUID requestKey,
      @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLong.class) Long expectedVersion,
      @NotBlank @Size(max = 500) @Pattern(regexp = "[^\\p{Cc}]*")
          @JsonDeserialize(using = StrictString.class) String reason) implements StrictRequest {}

  public record RefundRequest(
      @NotNull @JsonDeserialize(using = StrictUuid.class) UUID requestKey,
      @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLong.class) Long expectedVersion,
      @NotNull @AssertTrue @JsonDeserialize(using = StrictBoolean.class)
          Boolean cashReturnedConfirmed) implements StrictRequest {}

  /** Never returned as JSON. Controllers produce an authenticated, non-cacheable attachment. */
  public record DocumentDownload(byte[] bytes, String mediaType) {
    public DocumentDownload {
      if (bytes == null || mediaType == null) throw new IllegalArgumentException("Missing document.");
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() { return bytes.clone(); }
  }

  public static UUID parseUuid(String text) {
    if (text == null || !text.matches(UUID_TEXT)) {
      throw new InvalidRequestException("Provide a valid application or document identifier.");
    }
    return UUID.fromString(text);
  }

  private static String requireString(JsonParser parser) {
    if (parser.currentToken() != JsonToken.VALUE_STRING) {
      throw new InvalidRequestException("This request field must be a JSON string.");
    }
    return parser.getString();
  }

  public static final class StrictString extends StdDeserializer<String> {
    public StrictString() { super(String.class); }
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
      return requireString(parser);
    }
  }

  public static final class StrictUuid extends StdDeserializer<UUID> {
    public StrictUuid() { super(UUID.class); }
    @Override
    public UUID deserialize(JsonParser parser, DeserializationContext context) {
      return parseUuid(requireString(parser));
    }
  }

  public static final class StrictLong extends StdDeserializer<Long> {
    public StrictLong() { super(Long.class); }
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
      if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
        throw new InvalidRequestException("Expected version must be a JSON integer.");
      }
      return parser.getLongValue();
    }
  }

  public static final class StrictBoolean extends StdDeserializer<Boolean> {
    public StrictBoolean() { super(Boolean.class); }
    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context) {
      if (parser.currentToken() != JsonToken.VALUE_TRUE
          && parser.currentToken() != JsonToken.VALUE_FALSE) {
        throw new InvalidRequestException("Confirmation must be a JSON boolean.");
      }
      return parser.getBooleanValue();
    }
  }

  public static final class StrictDate extends StdDeserializer<LocalDate> {
    public StrictDate() { super(LocalDate.class); }
    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
      String value = requireString(parser);
      if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
        throw new InvalidRequestException("Date of birth must use YYYY-MM-DD.");
      }
      try {
        LocalDate date = LocalDate.parse(value);
        if (date.getYear() < 1) throw new InvalidRequestException("Provide a valid date of birth.");
        return date;
      } catch (DateTimeParseException invalid) {
        throw new InvalidRequestException("Provide a valid date of birth.");
      }
    }
  }

  public static final class StrictDocumentType extends StdDeserializer<DocumentType> {
    public StrictDocumentType() { super(DocumentType.class); }
    @Override
    public DocumentType deserialize(JsonParser parser, DeserializationContext context) {
      try {
        return DocumentType.valueOf(requireString(parser));
      } catch (IllegalArgumentException invalid) {
        throw new InvalidRequestException("Choose AADHAAR, PAN or ADDRESS_PROOF.");
      }
    }
  }
}
