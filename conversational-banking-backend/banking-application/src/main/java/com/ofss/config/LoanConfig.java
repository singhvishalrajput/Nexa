package com.ofss.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.*;

@Configuration
public class LoanConfig {
    @Bean("loanClock")
    public Clock loanClock() {
        return Clock.system(ZoneId.of("Asia/Kolkata"));
    }
}
