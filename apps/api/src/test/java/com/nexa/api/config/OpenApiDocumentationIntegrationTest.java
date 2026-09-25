package com.nexa.api.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.profiles.active=integration",
      "spring.datasource.url=jdbc:h2:mem:openapi;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "nexa.security.jwt.secret=openapi-documentation-test-secret-long-enough",
      "nexa.cors.allowed-origins=http://localhost:8000"
    })
@AutoConfigureMockMvc
class OpenApiDocumentationIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RequestMappingHandlerMapping requestMappingHandlerMapping;

  @Test
  void exposesSwaggerUiAndDocumentsPublicProtectedAndLegacyRoutes() throws Exception {
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());

    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").isNotEmpty())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.components.schemas.ApiError.properties.traceId").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/me'].get.security[0].bearerAuth").exists())
        .andExpect(jsonPath("$.paths['/api/v1/me'].get.x-required-roles[0]").value("AUTHENTICATED"))
        .andExpect(jsonPath("$.paths['/api/accounts'].get.x-required-roles[0]").value("ADMIN"));
  }

  @Test
  void documentsEveryMappedControllerOperation() throws Exception {
    JsonNode specification =
        objectMapper.readTree(
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());

    requestMappingHandlerMapping
        .getHandlerMethods()
        .forEach(
            (mapping, handler) -> {
              if (!handler.getBeanType().getPackageName().equals("com.nexa.api.controller")) return;
              mapping
                  .getPatternValues()
                  .forEach(
                      path -> {
                        assertTrue(
                            specification.path("paths").has(path),
                            () -> "OpenAPI is missing route " + path);
                        mapping
                            .getMethodsCondition()
                            .getMethods()
                            .forEach(
                                method ->
                                    assertTrue(
                                        specification.path("paths").path(path).has(method.name().toLowerCase()),
                                        () -> "OpenAPI is missing " + method + " " + path));
                      });
            });
  }
}
