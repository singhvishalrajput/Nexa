package com.nexa.api.service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Reviewed, versioned content only. Neither retrieval nor rendering calls a language model. */
@Service
public class KnowledgeBase {
  public record Entry(
      String id,
      String topic,
      String aspect,
      String scope,
      int version,
      LocalDate effectiveFrom,
      LocalDate effectiveUntil,
      boolean active,
      String audience,
      List<String> aliases,
      String answer,
      String hindi,
      String hinglish,
      String source) {
    public String localized(String language) {
      return "hi".equals(language) && hindi != null
          ? hindi
          : "hinglish".equals(language) && hinglish != null ? hinglish : answer;
    }
  }

  private final List<Entry> entries;

  private static List<Entry> load(ObjectMapper json) throws java.io.IOException {
    try (var input = new ClassPathResource("knowledge/nexa.json").getInputStream()) {
      return List.of(json.readValue(input, Entry[].class));
    }
  }

  @org.springframework.beans.factory.annotation.Autowired
  public KnowledgeBase(ObjectMapper json) throws java.io.IOException {
    this(load(json));
  }

  KnowledgeBase(List<Entry> entries) {
    this.entries = List.copyOf(entries);
    Set<String> versions = new HashSet<>();
    for (var entry : entries) {
      if (entry.id() == null
          || entry.topic() == null
          || entry.aspect() == null
          || !Set.of("NEXA", "GENERAL").contains(entry.scope())
          || !Set.of("CUSTOMER", "ADMIN").contains(entry.audience())
          || entry.version() < 1
          || entry.effectiveFrom() == null
          || entry.answer() == null
          || entry.answer().isBlank()
          || entry.source() == null
          || entry.aliases() == null
          || entry.aliases().isEmpty()
          || !versions.add(entry.id() + ":" + entry.version()))
        throw new IllegalStateException("Invalid knowledge entry: " + entry.id());
    }
  }

  private boolean available(Entry e, boolean admin, LocalDate today) {
    return e.active()
        && (!"ADMIN".equals(e.audience()) || admin)
        && !e.effectiveFrom().isAfter(today)
        && (e.effectiveUntil() == null || today.isBefore(e.effectiveUntil()));
  }

  public Set<String> topics(String text, boolean admin) {
    Map<String, Integer> scores = new TreeMap<>();
    for (var e : entries) {
      if (!available(e, admin, LocalDate.now())) continue;
      for (String alias : e.aliases()) {
        String normalized = normalize(alias);
        if (!Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + Pattern.quote(normalized) + "(?![\\p{L}\\p{N}])")
            .matcher(text)
            .find()) continue;
        scores.merge(e.topic(), normalized.length(), Math::max);
      }
    }
    // An explicit product qualifies generic rate words, even when its name is shorter.
    if (scores.size() > 1) scores.remove("interest");
    if (scores.size() > 1) scores.remove("nexa");
    if (scores.size() > 1) scores.remove("account");
    int best = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    Set<String> result = new TreeSet<>();
    scores.forEach(
        (topic, score) -> {
          if (score == best) result.add(topic);
        });
    return result;
  }

  public Optional<Entry> find(String topic, String aspect, boolean nexa, boolean admin) {
    return entries.stream()
        .filter(e -> available(e, admin, LocalDate.now()))
        .filter(e -> e.topic().equals(topic) && e.aspect().equals(aspect))
        .filter(e -> !nexa || e.scope().equals("NEXA"))
        .max(
            Comparator.comparing((Entry e) -> e.scope().equals("NEXA"))
                .thenComparing(Entry::effectiveFrom)
                .thenComparingInt(Entry::version));
  }

  public static String normalize(String text) {
    return text.toLowerCase(Locale.ROOT)
        .replace('’', '\'')
        .replaceAll("[?!।]", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }
}
