package com.nexa.api.service;

import com.nexa.api.exep.InvalidRequestException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only operational aggregates. Core timestamps are UTC; calendar buckets use India time. */
@Service
@Transactional(readOnly = true)
public class AdminAnalyticsService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter HOUR_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH", Locale.ROOT);
  private static final String CUSTOMERS = "c.role='CUSTOMER' AND c.user_id IS NOT NULL";
  private static final String ACCOUNTS = "a.account_category='CUSTOMER' AND " + CUSTOMERS;
  private static final List<String> APPLICATION_STATUSES = List.of("DRAFT", "PENDING_REVIEW",
      "CHANGES_REQUESTED", "APPROVED_AWAITING_CASH", "CASH_RECEIVED", "OPENED", "REJECTED",
      "CANCELLED", "REFUND_PENDING", "REFUNDED");
  private static final Set<String> ADMIN_BACKLOG = Set.of("PENDING_REVIEW", "APPROVED_AWAITING_CASH",
      "CASH_RECEIVED", "REFUND_PENDING");
  private static final String POSTED_AT = "COALESCE(t.completed_at,t.created_at)";
  private static final String CURRENCY = "COALESCE(t.currency_code,s.currency_code,d.currency_code)";
  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final Clock clock;

  public AdminAnalyticsService(JdbcTemplate db, CurrentUserProvider user, Clock clock) {
    this.db = db;
    this.user = user;
    this.clock = clock;
  }

  public record Money(String currencyCode, String amount) {}
  public record Count(String key, long count) {}
  public record Period(int days, String startDate, String endDate, String startAt, String endAt,
      String granularity) {}
  public record Snapshot(long customers, long activeCustomers, long customerAccounts,
      long activeCustomerAccounts, List<Money> activeDepositBalances, List<Count> accountTypes,
      List<Count> accountStatuses, List<Count> applicationStatuses, long applicationBacklog,
      long pendingLoans) {}
  public record Day(String date, long postedPayments, List<Money> paymentAmounts,
      long newCustomers, long newAccounts) {}
  public record Hour(String startAt, String endAt, long postedPayments, List<Money> paymentAmounts,
      long newCustomers, long newAccounts) {}
  public record Activity(long newCustomers, long newAccounts, long postedPayments,
      List<Money> paymentAmounts, List<Day> daily, List<Hour> hourly) {}
  public record Analytics(String generatedAt, String timezone, Period period, Snapshot snapshot,
      Activity activity) {}

  public Analytics read(int days) {
    authorize();
    if (!Set.of(1, 7, 30, 90).contains(days))
      throw new InvalidRequestException("Choose an analytics period of 1, 7, 30 or 90 days.");
    boolean hourly = days == 1;
    Instant now = clock.instant();
    LocalDate today = now.atZone(ZONE).toLocalDate();
    Instant startAt = hourly ? now.minus(24, ChronoUnit.HOURS)
        : today.minusDays(days - 1L).atStartOfDay(ZONE).toInstant();
    LocalDate start = startAt.atZone(ZONE).toLocalDate();
    Timestamp from = Timestamp.from(startAt), until = Timestamp.from(now);

    Map<String, Long> customerStatuses = counts("SELECT c.status,COUNT(*) FROM customers c WHERE "
        + CUSTOMERS + " GROUP BY c.status");
    Map<String, Long> accountStatuses = counts("SELECT a.status,COUNT(*) FROM accounts a JOIN customers c"
        + " ON c.id=a.customer_id WHERE " + ACCOUNTS + " GROUP BY a.status");
    Map<String, Long> accountTypes = counts("SELECT a.account_type,COUNT(*) FROM accounts a JOIN customers c"
        + " ON c.id=a.customer_id WHERE " + ACCOUNTS + " GROUP BY a.account_type");
    List<Money> deposits = db.query("SELECT a.currency_code,SUM(a.balance) FROM accounts a JOIN customers c"
        + " ON c.id=a.customer_id WHERE " + ACCOUNTS
        + " AND a.status='ACTIVE' AND a.account_type IN ('SAVINGS','CURRENT')"
        + " GROUP BY a.currency_code ORDER BY a.currency_code",
        (rs, row) -> new Money(rs.getString(1), money(rs.getBigDecimal(2))));
    Map<String, Long> applications = counts("SELECT p.status,COUNT(*) FROM account_applications p"
        + " JOIN customers c ON c.id=p.customer_id WHERE " + CUSTOMERS + " GROUP BY p.status");
    long pendingLoans = db.queryForObject("SELECT COUNT(*) FROM accounts a JOIN customers c"
        + " ON c.id=a.customer_id WHERE " + ACCOUNTS
        + " AND a.account_type='LOAN' AND a.product_status='PENDING_APPROVAL'", Long.class);
    Snapshot snapshot = new Snapshot(sum(customerStatuses), customerStatuses.getOrDefault("ACTIVE", 0L),
        sum(accountStatuses), accountStatuses.getOrDefault("ACTIVE", 0L), deposits,
        orderedCounts(List.of("SAVINGS", "CURRENT", "LOAN", "CARD", "CASH", "CLEARING"), accountTypes),
        orderedCounts(List.of("ACTIVE", "BLOCKED", "CLOSED"), accountStatuses),
        orderedCounts(APPLICATION_STATUSES, applications), applications.entrySet().stream()
            .filter(entry -> ADMIN_BACKLOG.contains(entry.getKey())).mapToLong(Map.Entry::getValue).sum(),
        pendingLoans);

    Map<String, MutableBucket> buckets = new LinkedHashMap<>();
    if (hourly) {
      for (var hour = startAt.atZone(ZONE).truncatedTo(ChronoUnit.HOURS); hour.toInstant().isBefore(now);
          hour = hour.plusHours(1)) {
        Instant begin = hour.toInstant(), end = hour.plusHours(1).toInstant();
        buckets.put(HOUR_KEY.format(hour), new MutableBucket(begin.isBefore(startAt) ? startAt : begin,
            end.isAfter(now) ? now : end));
      }
    } else {
      for (int day = 0; day < days; day++) buckets.put(start.plusDays(day).toString(), new MutableBucket(null, null));
    }
    db.query("SELECT " + bucketKey("c.created_at", hourly) + ",COUNT(*) FROM customers c WHERE " + CUSTOMERS
        + " AND c.created_at>=? AND c.created_at<? GROUP BY " + bucketKey("c.created_at", hourly),
        rs -> { requireBucket(buckets, rs.getString(1)).customers = rs.getLong(2); }, from, until);
    db.query("SELECT " + bucketKey("a.created_at", hourly) + ",COUNT(*) FROM accounts a JOIN customers c"
        + " ON c.id=a.customer_id WHERE " + ACCOUNTS
        + " AND a.created_at>=? AND a.created_at<? GROUP BY " + bucketKey("a.created_at", hourly),
        rs -> { requireBucket(buckets, rs.getString(1)).accounts = rs.getLong(2); }, from, until);

    // EXISTS keeps one payment per transaction regardless of its number of ledger legs. Accounts
    // establish currency because the original core did not persist it on every PAYMENT row.
    String paymentSql = "SELECT " + bucketKey(POSTED_AT, hourly) + "," + CURRENCY + ",COUNT(*),SUM(t.amount)"
        + " FROM transactions t LEFT JOIN accounts s ON s.id=t.source_account_id"
        + " LEFT JOIN accounts d ON d.id=t.destination_account_id"
        + " LEFT JOIN customers sc ON sc.id=s.customer_id LEFT JOIN customers dc ON dc.id=d.customer_id"
        + " WHERE t.record_kind='PAYMENT' AND t.status='SUCCESS'"
        + " AND " + POSTED_AT + ">=? AND " + POSTED_AT + "<?"
        + " AND EXISTS(SELECT 1 FROM journal_entries j WHERE j.transaction_id=t.id AND j.status='POSTED')"
        + " AND ((s.account_category='CUSTOMER' AND sc.role='CUSTOMER' AND sc.user_id IS NOT NULL)"
        + " OR (d.account_category='CUSTOMER' AND dc.role='CUSTOMER' AND dc.user_id IS NOT NULL))"
        + " AND (s.id IS NULL OR d.id IS NULL OR s.currency_code=d.currency_code)"
        + " AND (t.currency_code IS NULL OR t.currency_code=COALESCE(s.currency_code,d.currency_code))"
        + " GROUP BY " + bucketKey(POSTED_AT, hourly) + "," + CURRENCY;
    db.query(paymentSql, rs -> {
      MutableBucket bucket = requireBucket(buckets, rs.getString(1));
      bucket.payments += rs.getLong(3);
      bucket.amounts.put(rs.getString(2), rs.getBigDecimal(4));
    }, from, until);
    Map<String, BigDecimal> totals = new TreeMap<>();
    for (MutableBucket bucket : buckets.values())
      bucket.amounts.forEach((currency, amount) -> totals.merge(currency, amount, BigDecimal::add));
    List<Day> daily = new ArrayList<>();
    List<Hour> hours = new ArrayList<>();
    buckets.forEach((key, bucket) -> {
      // Include every period currency in each bucket, including intervals with no postings.
      Map<String, BigDecimal> amounts = new TreeMap<>();
      totals.keySet().forEach(currency -> amounts.put(currency, bucket.amounts.getOrDefault(currency, BigDecimal.ZERO)));
      if (hourly) hours.add(new Hour(bucket.startAt.toString(), bucket.endAt.toString(), bucket.payments,
          moneyList(amounts), bucket.customers, bucket.accounts));
      else daily.add(new Day(key, bucket.payments, moneyList(amounts), bucket.customers, bucket.accounts));
    });
    Activity activity = new Activity(buckets.values().stream().mapToLong(bucket -> bucket.customers).sum(),
        buckets.values().stream().mapToLong(bucket -> bucket.accounts).sum(),
        buckets.values().stream().mapToLong(bucket -> bucket.payments).sum(),
        moneyList(totals), List.copyOf(daily), List.copyOf(hours));
    return new Analytics(now.toString(), ZONE.getId(), new Period(days, start.toString(), today.toString(),
        startAt.toString(), now.toString(), hourly ? "HOUR" : "DAY"), snapshot, activity);
  }

  private void authorize() {
    if (db.queryForObject("SELECT COUNT(*) FROM customers WHERE user_id=? AND role='ADMIN' AND status='ACTIVE'",
        Long.class, user.userId()) != 1L) throw new AccessDeniedException("Administrator access required");
  }

  private Map<String, Long> counts(String sql) {
    Map<String, Long> values = new LinkedHashMap<>();
    db.query(sql, rs -> { values.put(rs.getString(1), rs.getLong(2)); });
    return values;
  }

  private static List<Count> orderedCounts(List<String> keys, Map<String, Long> counts) {
    return keys.stream().map(key -> new Count(key, counts.getOrDefault(key, 0L))).toList();
  }
  private static long sum(Map<String, Long> counts) { return counts.values().stream().mapToLong(Long::longValue).sum(); }
  private static String money(BigDecimal value) { return value.setScale(2).toPlainString(); }
  private static List<Money> moneyList(Map<String, BigDecimal> values) {
    return values.entrySet().stream().map(entry -> new Money(entry.getKey(), money(entry.getValue()))).toList();
  }
  private static String bucketKey(String column, boolean hourly) {
    return "TO_CHAR(" + column + " + INTERVAL '05:30' HOUR TO MINUTE,'"
        + (hourly ? "YYYY-MM-DD HH24" : "YYYY-MM-DD") + "')";
  }
  private static MutableBucket requireBucket(Map<String, MutableBucket> buckets, String key) {
    MutableBucket bucket = buckets.get(key);
    if (bucket == null) throw new IllegalStateException("Analytics aggregation returned a bucket outside its requested period.");
    return bucket;
  }
  private static final class MutableBucket {
    final Instant startAt, endAt;
    long customers, accounts, payments;
    final Map<String, BigDecimal> amounts = new TreeMap<>();
    MutableBucket(Instant startAt, Instant endAt) { this.startAt = startAt; this.endAt = endAt; }
  }
}
