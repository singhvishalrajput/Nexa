package com.nexa.api.nlp;

public interface EmbeddingProvider {
  double[] embed(String text);
}
