package com.nexa.api.service;
import com.nexa.api.beans.BankingContent;
import com.nexa.api.beans.Intent;


public interface DomainRouter {
  record Reply(String type, String message, BankingContent data, String errorCode) {}

  Reply route(Intent intent, EntityExtractor.Entities entities);
}
