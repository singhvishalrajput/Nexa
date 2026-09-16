package com.nexa.api.service;
import com.nexa.api.beans.Intent;


public interface IntentClassifier {
  record Match(Intent intent, double confidence) {}

  Match classify(String text);
}
