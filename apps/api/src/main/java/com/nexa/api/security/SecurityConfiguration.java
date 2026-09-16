package com.nexa.api.security;
import com.nexa.api.beans.Customer;


import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorWriter errorWriter)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/api/v1/health", "/actuator/health", "/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .requestMatchers(
                        "/api/accounts/**",
                        "/api/customers/**",
                        "/api/transactions/**",
                        "/api/journal-entries/**",
                        "/api/ledger-entries/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        "/api/v1/accounts/**",
                        "/api/v1/conversations/**",
                        "/api/v1/demo/**",
                        "/api/v1/money-transfers/**")
                    .hasAnyRole("CUSTOMER", "ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            errorWriter.write(
                                response,
                                HttpStatus.UNAUTHORIZED.value(),
                                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                                "UNAUTHORIZED",
                                "A valid access token is required."))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            errorWriter.write(
                                response,
                                HttpStatus.FORBIDDEN.value(),
                                HttpStatus.FORBIDDEN.getReasonPhrase(),
                                "ACCESS_DENIED",
                                "You are not allowed to access this resource.")))
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            errorWriter.write(
                                response,
                                HttpStatus.UNAUTHORIZED.value(),
                                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                                "UNAUTHORIZED",
                                "The access token is missing, invalid, or expired.")));
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  JwtEncoder jwtEncoder(@Value("${nexa.security.jwt.secret}") String secret) {
    OctetSequenceKey jwk =
        new OctetSequenceKey.Builder(secretKey(secret)).algorithm(JWSAlgorithm.HS256).build();
    JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
    return new NimbusJwtEncoder(jwkSource);
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${nexa.security.jwt.secret}") String secret,
      @Value("${nexa.security.jwt.issuer}") String issuer) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(secret)).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<Jwt>(JwtValidators.createDefaultWithIssuer(issuer)));
    return decoder;
  }

  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
    authoritiesConverter.setAuthoritiesClaimName("roles");
    authoritiesConverter.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
    authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
    return authenticationConverter;
  }

  private SecretKey secretKey(String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("NEXA_JWT_SECRET must contain at least 32 bytes.");
    }
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }
}
