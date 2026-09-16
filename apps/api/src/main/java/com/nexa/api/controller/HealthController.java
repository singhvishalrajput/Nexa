package com.nexa.api.controller;


import java.time.Instant;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
public class HealthController {

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "nexa-api",
                "timestamp", Instant.now().toString());
    }
}
