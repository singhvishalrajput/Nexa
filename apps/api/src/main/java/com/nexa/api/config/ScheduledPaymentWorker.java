package com.nexa.api.config;

import com.nexa.api.service.ScheduledPaymentService;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Only durable authorizations in V30 are eligible. Integration tests invoke the service explicitly. */
@Configuration
@EnableScheduling
@Profile("!integration")
@ConditionalOnProperty(name="nexa.scheduled-payments.enabled",havingValue="true")
public class ScheduledPaymentWorker {
  private final ScheduledPaymentService service;
  public ScheduledPaymentWorker(ScheduledPaymentService service) { this.service=service; }
  @Scheduled(fixedDelayString="${nexa.scheduled-payments.poll-ms:30000}", initialDelayString="${nexa.scheduled-payments.poll-ms:30000}")
  public void runDue() {
    for(String id:service.dueIds()) {
      try { service.executeDue(id); }
      catch(RuntimeException failure) {
        // The transaction rolled back. Leave the instruction durable for a later safe retry.
        LoggerFactory.getLogger(getClass()).warn("Scheduled payment {} could not be processed; it remains available for retry.",id);
      }
    }
  }
}
