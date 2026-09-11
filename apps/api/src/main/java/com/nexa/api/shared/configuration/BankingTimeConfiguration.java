package com.nexa.api.shared.configuration;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
/** Banking core timestamps and migrated historical timestamps use UTC. */
@Configuration
class BankingTimeConfiguration {
 @PostConstruct void useUtc() { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")); }
}
