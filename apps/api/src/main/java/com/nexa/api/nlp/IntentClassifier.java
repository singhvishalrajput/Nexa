package com.nexa.api.nlp;

public interface IntentClassifier {
  record Match(Intent intent, double confidence) {}

  Match classify(String text);
}
