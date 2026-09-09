package com.nexa.api.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.nexa.api.shared.errors.ApiError;

@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, int status, String title, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(
                "https://api.nexa/errors/" + code.toLowerCase().replace('_', '-'),
                title,
                status,
                code,
                detail,
                MDC.get("correlationId"),
                Instant.now(),
                List.of());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
