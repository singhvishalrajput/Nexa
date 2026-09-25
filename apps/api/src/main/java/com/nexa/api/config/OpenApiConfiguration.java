package com.nexa.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata shared by all Spring MVC controller routes. */
@Configuration
public class OpenApiConfiguration {

  private static final String API_ERROR_REF = "#/components/schemas/ApiError";
  private static final String BEARER_AUTH = "bearerAuth";

  @Bean
  OpenAPI nexaOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Nexa Banking API")
                .version("v1")
                .description(
                    "REST APIs for Nexa banking, administration, and conversational workflows."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token returned by /api/v1/auth/login or /register."))
                .addSchemas("ApiError", apiErrorSchema())
                .addSchemas("ApiErrorFieldError", apiErrorFieldErrorSchema())
                .addParameters(
                    "CorrelationId",
                    new HeaderParameter()
                        .name(CorrelationIdFilter.HEADER_NAME)
                        .description("Optional request correlation identifier (1-128 letters, digits, dots, underscores, or hyphens).")
                        .required(false)
                        .schema(new StringSchema().maxLength(128)))
                .addHeaders(
                    CorrelationIdFilter.HEADER_NAME,
                    new io.swagger.v3.oas.models.headers.Header()
                        .description("Correlation identifier assigned to this response.")
                        .schema(new StringSchema()))
                .addResponses("BadRequest", errorResponse("The request is invalid or fails validation."))
                .addResponses("Unauthorized", errorResponse("A valid JWT access token is required."))
                .addResponses("Forbidden", errorResponse("The authenticated user lacks the required role."))
                .addResponses("NotFound", errorResponse("The requested resource does not exist."))
                .addResponses("Conflict", errorResponse("The request conflicts with the current resource state."))
                .addResponses("ServiceUnavailable", errorResponse("Banking information is temporarily unavailable."))
                .addResponses("InternalError", errorResponse("The request could not be completed.")));
  }

  @Bean
  GlobalOpenApiCustomizer commonOperationDocumentation() {
    return openApi -> {
      if (openApi.getPaths() == null) return;
      openApi.getPaths().forEach(
          (path, pathItem) ->
              pathItem.readOperations().forEach(operation -> documentOperation(path, operation)));
    };
  }

  private void documentOperation(String path, io.swagger.v3.oas.models.Operation operation) {
    operation.addParametersItem(new HeaderParameter().$ref("#/components/parameters/CorrelationId"));
    ApiResponses responses = operation.getResponses();
    if (responses == null) {
      responses = new ApiResponses();
      operation.setResponses(responses);
    }
    addResponse(responses, "400", "#/components/responses/BadRequest");
    addResponse(responses, "409", "#/components/responses/Conflict");
    addResponse(responses, "503", "#/components/responses/ServiceUnavailable");
    addResponse(responses, "500", "#/components/responses/InternalError");
    if (path.contains("{")) addResponse(responses, "404", "#/components/responses/NotFound");
    responses.forEach(
        (status, response) -> {
          if (response.get$ref() == null) {
            response.setHeaders(
                Map.of(
                    CorrelationIdFilter.HEADER_NAME,
                    new io.swagger.v3.oas.models.headers.Header()
                        .$ref("#/components/headers/" + CorrelationIdFilter.HEADER_NAME)));
          }
        });

    if (isPublic(path)) return;

    operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    addResponse(responses, "401", "#/components/responses/Unauthorized");
    addResponse(responses, "403", "#/components/responses/Forbidden");
    if (requiresAdmin(path)) {
      operation.addExtension("x-required-roles", List.of("ADMIN"));
    } else if (requiresCustomerOrAdmin(path)) {
      operation.addExtension("x-required-roles", List.of("CUSTOMER", "ADMIN"));
    } else {
      operation.addExtension("x-required-roles", List.of("AUTHENTICATED"));
    }
  }

  private boolean isPublic(String path) {
    return path.equals("/api/v1/health") || path.startsWith("/api/v1/auth/");
  }

  private boolean requiresAdmin(String path) {
    return path.startsWith("/api/v1/admin/")
        || path.startsWith("/api/accounts")
        || path.startsWith("/api/customers")
        || path.startsWith("/api/transactions")
        || path.startsWith("/api/journal-entries")
        || path.startsWith("/api/ledger-entries");
  }

  private boolean requiresCustomerOrAdmin(String path) {
    return path.startsWith("/api/v1/accounts")
        || path.startsWith("/api/v1/conversations")
        || path.startsWith("/api/v1/demo")
        || path.startsWith("/api/v1/money-transfers");
  }

  private void addResponse(ApiResponses responses, String status, String reference) {
    if (!responses.containsKey(status)) responses.addApiResponse(status, new ApiResponse().$ref(reference));
  }

  private ApiResponse errorResponse(String description) {
    return new ApiResponse()
        .description(description)
        .headers(
            Map.of(
                CorrelationIdFilter.HEADER_NAME,
                new io.swagger.v3.oas.models.headers.Header()
                    .$ref("#/components/headers/" + CorrelationIdFilter.HEADER_NAME)))
        .content(
            new Content()
                .addMediaType(
                    org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                    new MediaType().schema(new Schema<>().$ref(API_ERROR_REF))));
  }

  private Schema<?> apiErrorSchema() {
    return new ObjectSchema()
        .description("Structured error returned by the API exception handlers.")
        .addProperty("type", new StringSchema().format("uri"))
        .addProperty("title", new StringSchema())
        .addProperty("status", new IntegerSchema().format("int32"))
        .addProperty("code", new StringSchema())
        .addProperty("detail", new StringSchema())
        .addProperty("traceId", new StringSchema())
        .addProperty("timestamp", new StringSchema().format("date-time"))
        .addProperty("fieldErrors", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/ApiErrorFieldError")))
        .addProperty(
            "response",
            new ObjectSchema()
                .addProperty("type", new StringSchema().example("ERROR"))
                .addProperty("message", new StringSchema())
                .addProperty("errorCode", new StringSchema()));
  }

  private Schema<?> apiErrorFieldErrorSchema() {
    return new ObjectSchema()
        .addProperty("field", new StringSchema())
        .addProperty("message", new StringSchema());
  }
}
