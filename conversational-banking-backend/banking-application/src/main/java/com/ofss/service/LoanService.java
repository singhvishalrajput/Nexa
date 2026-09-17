package com.ofss.service;

import java.util.List;
import com.ofss.beans.*;

public interface LoanService {
    LoanResponse apply(LoanApplicationRequest request, Long customerId);
    List<LoanResponse> byCustomer(Long customerId);
    LoanResponse getById(Long id, Long customerId);
    LoanResponse accept(Long id, Long customerId);
    List<LoanInstallmentResponse> schedule(Long id, Long customerId);
    List<LoanPaymentResponse> payments(Long id, Long customerId);
    LoanPaymentResponse payInstallment(Long id, Long installmentId, Long customerId);
}
