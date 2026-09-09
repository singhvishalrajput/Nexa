package com.nexa.api.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexa.api.identity.CurrentUserProvider;
import com.nexa.api.identity.UserQueryService;
import com.nexa.api.identity.UserQueryService.UserSummary;

@Service
public class CustomerQueryService {

    private final CurrentUserProvider currentUserProvider;
    private final UserQueryService userQueryService;

    CustomerQueryService(
            CurrentUserProvider currentUserProvider,
            UserQueryService userQueryService) {
        this.currentUserProvider = currentUserProvider;
        this.userQueryService = userQueryService;
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse currentProfile() {
        String userId = currentUserProvider.userId();
        return toResponse(userQueryService.requireUser(userId));
    }

    @Transactional
    public CustomerProfileResponse updateCurrentProfile(UpdateProfileRequest request) {
        String userId = currentUserProvider.userId();
        return toResponse(userQueryService.updateProfile(userId, request.fullName(), request.phoneNumber()));
    }

    private CustomerProfileResponse toResponse(UserSummary user) {
        return new CustomerProfileResponse(
                user.id(),
                user.id(),
                user.fullName(),
                user.email(),
                user.phoneNumber(),
                user.status(),
                user.role());
    }
}
