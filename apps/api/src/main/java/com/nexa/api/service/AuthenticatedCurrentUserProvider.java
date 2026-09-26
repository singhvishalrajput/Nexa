package com.nexa.api.service;


import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class AuthenticatedCurrentUserProvider implements CurrentUserProvider {

    private final com.nexa.api.repository.UserRepository users;

    AuthenticatedCurrentUserProvider(com.nexa.api.repository.UserRepository users) {
        this.users = users;
    }

    @Override
    public String userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        String id = authentication.getName();
        var user = users.findById(id).orElseThrow(() ->
                new com.nexa.api.exep.UnauthorizedException("This user account is unavailable."));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new com.nexa.api.exep.UnauthorizedException("This user account is not active.");
        }
        return id;
    }
}
