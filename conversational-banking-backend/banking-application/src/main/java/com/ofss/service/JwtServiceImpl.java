package com.ofss.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import com.ofss.beans.CustomerCredential;

@Service
public class JwtServiceImpl implements JwtService {
	@Autowired
	JwtEncoder jwtEncoder;

	@Value("${app.jwt.expiration-minutes}")
	private long expirationMinutes;

	@Override
	public String generateToken(CustomerCredential credential) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(getExpirationSeconds());

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("banking-application")
				.issuedAt(now)
				.expiresAt(expiresAt)
				.subject(credential.getCustomerId().toString())
				.claim("role", credential.getRole().name())
				.build();

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	@Override
	public long getExpirationSeconds() {
		return expirationMinutes * 60;
	}
}
