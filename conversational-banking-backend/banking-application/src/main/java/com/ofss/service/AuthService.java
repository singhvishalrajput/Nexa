package com.ofss.service;

import com.ofss.beans.*;

public interface AuthService {
	LoginResponse register(RegisterRequest request);

	LoginResponse login(LoginRequest request);
}
