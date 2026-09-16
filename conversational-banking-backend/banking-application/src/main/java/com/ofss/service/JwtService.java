package com.ofss.service;

import com.ofss.beans.CustomerCredential;

public interface JwtService {
	String generateToken(CustomerCredential credential);

	long getExpirationSeconds();
}
