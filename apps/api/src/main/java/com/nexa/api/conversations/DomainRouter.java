package com.nexa.api.conversations;

import com.nexa.api.nlp.*;

public interface DomainRouter {
  record Reply(String type, String message, BankingContent data, String errorCode) {}

  Reply route(Intent intent, EntityExtractor.Entities entities);
}
