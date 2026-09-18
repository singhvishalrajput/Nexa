package com.nexa.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/** Banking core timestamps and migrated historical timestamps use UTC. */
@Configuration
class BankingTimeConfiguration {
  @org.springframework.context.annotation.Bean
  java.time.Clock bankingClock() {
    return java.time.Clock.systemUTC();
  }

  @PostConstruct
  void useUtc() {
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
  }
}
