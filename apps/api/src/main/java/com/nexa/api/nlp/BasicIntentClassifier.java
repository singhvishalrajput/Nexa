package com.nexa.api.nlp;

import java.util.Locale;

public class BasicIntentClassifier implements IntentClassifier {
  private final EmbeddingProvider embeddings;
  private final VectorIndex index;
  private final double threshold;

  public BasicIntentClassifier(EmbeddingProvider embeddings, VectorIndex index, double threshold) {
    if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1)
      throw new IllegalArgumentException("Invalid NLP threshold.");
    this.embeddings = embeddings;
    this.index = index;
    this.threshold = threshold;
  }

  public Match classify(String input) {
    String text = input.toLowerCase(Locale.ROOT).replace("’", "'");
    // Never discard negation, conditional instructions or compound requests.
    if (text.matches(".*\\b(dont|don't|not|never|unless|if|and|then|tomorrow|every)\\b.*")
        || text.contains("coming up") && !text.contains("payment"))
      return new Match(Intent.UNKNOWN, 0);
    // Identifiers and literal amounts are entities, not semantic vector features.
    String semantic =
        text.replaceAll("\\b(?:acc|txn|mnd|bil|crd|ben|sch|lon|trf)_[a-z0-9_]+\\b", "")
            .replaceAll("₹[0-9,.]+|[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?", "");
    if (text.matches(
        "(?:please )?(?:send|transfer|pay|move) (?:₹|rs\\.? ?|inr )?[0-9,.]+ (?:rupees? )?to"
            + " [\\p{L}][\\p{L} '-]{0,59}[.!?]?")) semantic = "transfer money";
    var hits = index.search(embeddings.embed(semantic));
    if (hits.isEmpty()) return new Match(Intent.UNKNOWN, 0);
    var best = hits.get(0);
    boolean ambiguous = hits.size() > 1 && best.score() - hits.get(1).score() < 0.08;
    return new Match(
        best.score() < threshold || ambiguous ? Intent.UNKNOWN : best.intent(), best.score());
  }
}
