package com.nexa.api.nlp;

import java.util.*;

public class InMemoryVectorIndex implements VectorIndex {
  private final List<Entry> entries;

  public InMemoryVectorIndex(IntentRepository repository, EmbeddingProvider embeddings) {
    entries =
        repository.examples().stream()
            .map(e -> new Entry(e.intent(), embeddings.embed(e.text())))
            .toList();
  }

  public List<Hit> search(double[] vector) {
    Map<Intent, Double> scores = new EnumMap<>(Intent.class);
    for (Entry entry : entries) {
      if (entry.vector().length != vector.length)
        throw new IllegalStateException("Embedding dimension mismatch.");
      double dot = 0, a = 0, b = 0;
      for (int i = 0; i < vector.length; i++) {
        dot += vector[i] * entry.vector()[i];
        a += vector[i] * vector[i];
        b += entry.vector()[i] * entry.vector()[i];
      }
      double score = a * b == 0 ? 0 : Math.max(0, Math.min(1, dot / Math.sqrt(a * b)));
      scores.merge(entry.intent(), score, Math::max);
    }
    return scores.entrySet().stream()
        .map(e -> new Hit(e.getKey(), e.getValue()))
        .sorted(Comparator.comparingDouble(Hit::score).reversed())
        .toList();
  }
}
