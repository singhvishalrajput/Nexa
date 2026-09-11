package com.nexa.api.health;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nexa.api.shared.configuration.CorrelationIdFilter;
import com.nexa.api.shared.configuration.CorsConfiguration;
import com.nexa.api.security.SecurityConfiguration;
import com.nexa.api.security.SecurityErrorWriter;

@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
        CorrelationIdFilter.class,
        CorsConfiguration.class,
        SecurityConfiguration.class,
        SecurityErrorWriter.class
})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiHealthReportsServiceStatusAndCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", not(emptyOrNullString())))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("nexa-api"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void apiHealthReturnsProvidedCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Correlation-ID", "test-request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "test-request-123"));
    }

    @Test
    void allowsPreflightRequestsFromLocalFrontend() throws Exception {
        mockMvc.perform(options("/api/v1/health")
                        .header("Origin", "http://localhost:8000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8000"));
    }
}
