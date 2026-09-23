package com.nexa.api.service;

import com.nexa.api.exep.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class LoanDocumentService {
  public static final int MAX_BYTES = 5 * 1024 * 1024;
  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final BusinessDateResolver dates;
  private final Clock clock;

  public LoanDocumentService(JdbcTemplate db, CurrentUserProvider user, BusinessDateResolver dates, Clock clock) {
    this.db = db; this.user = user; this.dates = dates; this.clock = clock;
  }

  public record Document(String id, String month, String fileName, String mediaType, int size,
      LocalDateTime uploadedAt, String verifiedBy, LocalDateTime verifiedAt) {}
  public record Bundle(List<String> requiredMonths, List<Document> documents) {}
  public record Download(String fileName, String mediaType, byte[] content) {}
  private record Upload(String month, String name, String type, byte[] bytes, String hash) {}

  public List<String> requiredMonths() {
    return months(YearMonth.now(clock.withZone(dates.zone())));
  }

  private List<String> months(YearMonth applicationMonth) {
    return List.of(applicationMonth.minusMonths(3).toString(),
        applicationMonth.minusMonths(2).toString(), applicationMonth.minusMonths(1).toString());
  }

  private List<String> requiredMonths(Map<String, Object> loan) {
    var created = ((java.sql.Timestamp) loan.get("CREATED_AT")).toLocalDateTime();
    return months(YearMonth.from(created.atZone(ZoneOffset.UTC).withZoneSameInstant(dates.zone())));
  }

  private Map<String, Object> accessible(String id, boolean lock) {
    var rows = db.queryForList(
        "SELECT a.* FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE a.product_id=?"
        + " AND a.account_type='LOAN' AND (c.user_id=? OR EXISTS (SELECT 1 FROM customers admin"
        + " WHERE admin.user_id=? AND admin.role='ADMIN' AND admin.status='ACTIVE'))"
        + (lock ? " FOR UPDATE OF a.balance" : ""), id, user.userId(), user.userId());
    if (rows.isEmpty()) throw new ResourceNotFoundException("Loan not found");
    return rows.get(0);
  }

  private List<Document> documents(long account) {
    return db.query("SELECT id,salary_month,file_name,media_type,file_size,uploaded_at,verified_by,verified_at"
        + " FROM loan_salary_slips WHERE loan_account_id=? ORDER BY salary_month",
        (r, n) -> new Document(r.getString("id"), r.getString("salary_month"), r.getString("file_name"),
            r.getString("media_type"), r.getInt("file_size"), r.getTimestamp("uploaded_at").toLocalDateTime(),
            r.getString("verified_by"), r.getTimestamp("verified_at") == null ? null
                : r.getTimestamp("verified_at").toLocalDateTime()), account);
  }

  public Bundle list(String id) {
    var loan = accessible(id, false);
    return new Bundle(requiredMonths(loan), documents(((Number) loan.get("ID")).longValue()));
  }

  /** Called inside the application transaction; failed validation rolls back the application too. */
  public Bundle attach(String id, List<String> months, List<MultipartFile> files) {
    var loan = accessible(id, true);
    long account = ((Number) loan.get("ID")).longValue();
    if (db.queryForObject("SELECT COUNT(*) FROM customers WHERE id=? AND user_id=?", Integer.class,
        loan.get("CUSTOMER_ID"), user.userId()) != 1)
      throw new ResourceNotFoundException("Loan not found");
    List<String> required = requiredMonths(loan);
    if (months == null || files == null || months.size() != 3 || files.size() != 3
        || !new HashSet<>(months).equals(new HashSet<>(required)))
      throw new InvalidRequestException("Upload one salary slip for each required month: " + String.join(", ", required));
    List<Upload> uploads = new ArrayList<>();
    Set<String> hashes = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      MultipartFile file = files.get(i);
      if (file.isEmpty() || file.getSize() > MAX_BYTES)
        throw new InvalidRequestException("Each salary slip must be non-empty and at most 5 MB");
      byte[] bytes;
      try { bytes = file.getBytes(); }
      catch (IOException e) { throw new InvalidRequestException("Unable to read salary slip"); }
      String type = detectType(bytes);
      if (!type.equals(file.getContentType()))
        throw new InvalidRequestException("Salary slips must be genuine PDF, PNG or JPEG files matching their file type");
      String hash;
      try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
      catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
      if (!hashes.add(hash)) throw new InvalidRequestException("Upload a different salary slip for each month");
      String name = Optional.ofNullable(file.getOriginalFilename()).orElse("salary-slip");
      name = name.replace('\\', '/');
      name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._ -]", "_");
      if (name.isBlank()) name = "salary-slip";
      // Never download a PDF/image under an executable or HTML extension supplied by a client.
      String extension = type.equals("application/pdf") ? ".pdf" : type.equals("image/png") ? ".png" : ".jpg";
      if (name.length() > 150) name = name.substring(0, 150);
      if (!name.toLowerCase(Locale.ROOT).endsWith(extension)) name += extension;
      uploads.add(new Upload(months.get(i), name, type, bytes, hash));
    }
    var existing = db.queryForList("SELECT salary_month,sha256 FROM loan_salary_slips WHERE loan_account_id=?", account);
    if (!existing.isEmpty()) {
      boolean same = existing.size() == 3 && uploads.stream().allMatch(u -> existing.stream()
          .anyMatch(r -> u.month().equals(r.get("SALARY_MONTH")) && u.hash().equals(r.get("SHA256"))));
      if (!same) throw new ConflictException("Salary slips are already submitted and cannot be replaced");
      return new Bundle(required, documents(account));
    }
    if (!"PENDING_APPROVAL".equals(loan.get("PRODUCT_STATUS")))
      throw new ConflictException("Salary slips can only be submitted for a pending application");
    for (var upload : uploads)
      db.update("INSERT INTO loan_salary_slips(id,loan_account_id,salary_month,file_name,media_type,"
          + "file_size,sha256,file_content,uploaded_at) VALUES(?,?,?,?,?,?,?,?,?)",
          "D-" + UUID.randomUUID(), account, upload.month(), upload.name(), upload.type(),
          upload.bytes().length, upload.hash(), upload.bytes(), LocalDateTime.now(clock));
    return new Bundle(required, documents(account));
  }

  private String detectType(byte[] b) {
    if (b.length > 8 && new String(b, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) return "application/pdf";
    if (b.length > 8 && Arrays.equals(Arrays.copyOf(b, 8),
        new byte[] {(byte)137,80,78,71,13,10,26,10})) return "image/png";
    if (b.length > 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255) return "image/jpeg";
    throw new InvalidRequestException("Only PDF, PNG and JPEG salary slips are supported");
  }

  public Download download(String id, String documentId) {
    var loan = accessible(id, false);
    var rows = db.query("SELECT file_name,media_type,file_content FROM loan_salary_slips"
        + " WHERE loan_account_id=? AND id=?",
        (r, n) -> new Download(r.getString("file_name"), r.getString("media_type"), r.getBytes("file_content")),
        loan.get("ID"), documentId);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Salary slip not found");
    return rows.get(0);
  }

  /** The caller holds the loan lock and has authorized the administrator. */
  public void verifyForApproval(Map<String, Object> loan, List<String> verifiedIds) {
    long account = ((Number) loan.get("ID")).longValue();
    var slips = documents(account);
    if (slips.size() != 3 || !new HashSet<>(slips.stream().map(Document::month).toList())
        .equals(new HashSet<>(requiredMonths(loan))))
      throw new InvalidRequestException("All three required salary slips must be uploaded before approval");
    if (verifiedIds == null || verifiedIds.size() != 3
        || !new HashSet<>(verifiedIds).equals(new HashSet<>(slips.stream().map(Document::id).toList())))
      throw new InvalidRequestException("Confirm verification of each of the three salary slips before approval");
    db.update("UPDATE loan_salary_slips SET verified_by=?,verified_at=? WHERE loan_account_id=?",
        user.userId(), LocalDateTime.now(clock), account);
  }
}
