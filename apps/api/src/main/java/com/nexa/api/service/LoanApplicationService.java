package com.nexa.api.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LoanApplicationService {
  private final CreditMandateService loans;
  private final LoanDocumentService documents;
  public LoanApplicationService(CreditMandateService loans, LoanDocumentService documents) {
    this.loans = loans; this.documents = documents;
  }
  @Transactional
  public Map<String, Object> submit(CreditMandateService.LoanRequest request,
      List<String> months, List<MultipartFile> files) {
    var loan = loans.createLoan(request);
    documents.attach(loan.get("PRODUCT_ID").toString(), months, files);
    return loan;
  }
}
