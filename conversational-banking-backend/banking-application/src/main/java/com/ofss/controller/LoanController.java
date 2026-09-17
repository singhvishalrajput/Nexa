package com.ofss.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.ofss.beans.*;
import com.ofss.service.*;

@RestController
@RequestMapping("/api/loans")
public class LoanController {
    @Autowired
    LoanService service;

    @Autowired
    LoanCalculationService calculation;

    @Autowired
    AuthorizationService authorizationService;

    @PostMapping("/quote")
    public LoanQuoteResponse quote(@RequestBody LoanQuoteRequest request) {
        return calculation.quote(request);
    }

    @PostMapping
    public ResponseEntity<LoanResponse> apply(@RequestBody LoanApplicationRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.apply(request, authorizationService.getCustomerId(authentication)));
    }

    @GetMapping
    public List<LoanResponse> all(Authentication authentication) {
        return service.byCustomer(authorizationService.getCustomerId(authentication));
    }

    @GetMapping("/{id}")
    public LoanResponse one(@PathVariable Long id, Authentication authentication) {
        return service.getById(id, authorizationService.getCustomerId(authentication));
    }

    @PostMapping("/{id}/accept")
    public LoanResponse accept(@PathVariable Long id, Authentication authentication) {
        return service.accept(id, authorizationService.getCustomerId(authentication));
    }

    @GetMapping("/{id}/schedule")
    public List<LoanInstallmentResponse> schedule(@PathVariable Long id, Authentication authentication) {
        return service.schedule(id, authorizationService.getCustomerId(authentication));
    }

    @GetMapping("/{id}/payments")
    public List<LoanPaymentResponse> payments(@PathVariable Long id, Authentication authentication) {
        return service.payments(id, authorizationService.getCustomerId(authentication));
    }

    @PostMapping("/{id}/installments/{installmentId}/pay")
    public LoanPaymentResponse pay(@PathVariable Long id, @PathVariable Long installmentId, Authentication authentication) {
        return service.payInstallment(id, installmentId, authorizationService.getCustomerId(authentication));
    }
}
