package com.nexa.api.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexa.api.customer.CustomerProfileResponse;
import com.nexa.api.customer.CustomerQueryService;
import com.nexa.api.customer.MeController;
import com.nexa.api.health.HealthController;
import com.nexa.api.shared.configuration.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({HealthController.class, MeController.class})
@ActiveProfiles("test")
@Import({SecurityConfiguration.class, SecurityErrorWriter.class, CorrelationIdFilter.class})
class SecurityConfigurationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CustomerQueryService customerQueryService;

  @Test
  void allowsPublicHealthWithoutToken() throws Exception {
    mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
  }

  @Test
  void rejectsProtectedEndpointWithoutToken() throws Exception {
    mockMvc
        .perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void protectsNewBankingRoutes() throws Exception {
    for (String route :
        java.util.List.of(
            "mandates",
            "bills",
            "credit-cards",
            "cards",
            "beneficiaries",
            "scheduled-payments",
            "loans",
            "transactions",
            "transfers/trf_1"))
      mockMvc.perform(get("/api/v1/" + route)).andExpect(status().isUnauthorized());
  }

  @Test
  void allowsProtectedEndpointWithAuthenticatedJwt() throws Exception {
    when(customerQueryService.currentProfile())
        .thenReturn(
            new CustomerProfileResponse(
                "cst_01JDEMO000000000000001",
                "usr_01JDEMO000000000000001",
                "Vishal Singh",
                "vishal@example.com",
                "+91 90000 00482",
                "ACTIVE",
                "CUSTOMER", null));

    mockMvc
        .perform(
            get("/api/v1/me")
                .with(
                    jwt()
                        .jwt(
                            jwt ->
                                jwt.subject("usr_01JDEMO000000000000001")
                                    .claim("roles", java.util.List.of("CUSTOMER")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("vishal@example.com"));
  }
}
