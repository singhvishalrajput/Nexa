package com.nexa.api.service;


public interface EmbeddingProvider {
  double[] embed(String text);
}
