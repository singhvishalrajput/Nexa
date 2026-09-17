package com.ofss.service;

import java.time.LocalDate;
import java.util.List;
import com.ofss.beans.*;

public interface LoanCalculationService {
    LoanQuoteResponse quote(LoanQuoteRequest request);
    List<LoanInstallment> schedule(Loan loan, LocalDate disbursementDate);
}
