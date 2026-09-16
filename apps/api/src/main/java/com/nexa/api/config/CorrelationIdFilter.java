package com.nexa.api.config;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Correlation-ID";
  private static final String MDC_KEY = "correlationId";
  private static final int MAX_LENGTH = 128;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId =
        validIncomingId(request.getHeader(HEADER_NAME))
            ? request.getHeader(HEADER_NAME)
            : UUID.randomUUID().toString();

    MDC.put(MDC_KEY, correlationId);
    response.setHeader(HEADER_NAME, correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private boolean validIncomingId(String value) {
    return value != null
        && !value.isBlank()
        && value.length() <= MAX_LENGTH
        && value.matches("[A-Za-z0-9._-]+");
  }
}
