package com.nexa.api.service;
import com.nexa.api.beans.Customer;


import java.time.*;
import java.time.temporal.TemporalAdjusters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The sole conversion point for customer-facing calendar dates. Database timestamps remain UTC. */
@Component
public class BusinessDateResolver {
  public enum RelativeDate { TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_WEEKEND, LAST_WEEKEND }
  public record Range(ZonedDateTime start, ZonedDateTime end, String label) {
    public Range {
      if (!start.isBefore(end)) throw new IllegalArgumentException("Date range must have a positive duration.");
    }
    public LocalDate startDate() { return start.toLocalDate(); }
    public LocalDate endDateInclusive() { return end.minusNanos(1).toLocalDate(); }
  }

  private final Clock clock;
  private final ZoneId zone;

  @org.springframework.beans.factory.annotation.Autowired
  public BusinessDateResolver(@Value("${nexa.business-timezone:Asia/Kolkata}") String zone) {
    this(Clock.systemUTC(), ZoneId.of(zone));
  }

  public BusinessDateResolver(Clock clock, ZoneId zone) {
    this.clock = clock;
    this.zone = zone;
  }

  public ZoneId zone() { return zone; }
  public Range resolve(RelativeDate value) { return resolve(value, LocalDate.now(clock.withZone(zone))); }

  public Range resolve(RelativeDate value, LocalDate today) {
    LocalDate start;
    LocalDate end;
    switch (value) {
      case TODAY -> { start = today; end = today.plusDays(1); }
      case YESTERDAY -> { start = today.minusDays(1); end = today; }
      case THIS_WEEK -> { start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); end = start.plusDays(7); }
      case LAST_WEEK -> { end = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); start = end.minusDays(7); }
      case THIS_MONTH -> { start = today.withDayOfMonth(1); end = start.plusMonths(1); }
      case LAST_MONTH -> { end = today.withDayOfMonth(1); start = end.minusMonths(1); }
      case THIS_WEEKEND -> { start = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)); end = start.plusDays(2); }
      case LAST_WEEKEND -> { start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)); end = start.plusDays(2); }
      default -> throw new IllegalArgumentException("Unknown relative date.");
    }
    return range(start, end, start.equals(end.minusDays(1)) ? start.toString() : start + " to " + end.minusDays(1));
  }

  public Range range(LocalDate start, LocalDate endExclusive, String label) {
    return new Range(start.atStartOfDay(zone), endExclusive.atStartOfDay(zone), label);
  }
}
