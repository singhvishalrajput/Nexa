package com.nexa.api.controller;
import com.nexa.api.beans.CustomerProfileResponse;
import com.nexa.api.beans.UpdateProfileRequest;
import com.nexa.api.service.CustomerQueryService;


import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final CustomerQueryService customerQueryService;

    MeController(CustomerQueryService customerQueryService) {
        this.customerQueryService = customerQueryService;
    }

    @GetMapping
    public CustomerProfileResponse me() {
        return customerQueryService.currentProfile();
    }

    @PatchMapping
    public CustomerProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return customerQueryService.updateCurrentProfile(request);
    }
}
