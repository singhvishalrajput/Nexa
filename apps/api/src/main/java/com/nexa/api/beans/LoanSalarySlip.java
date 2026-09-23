package com.nexa.api.beans;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Schema mapping only. Document bytes must never be serialized as an account/API entity. */
@Entity
@Table(name = "loan_salary_slips", uniqueConstraints = {
    @UniqueConstraint(name = "uk_loan_salary_month", columnNames = {"loan_account_id", "salary_month"}),
    @UniqueConstraint(name = "uk_loan_salary_content", columnNames = {"loan_account_id", "sha256"})
})
public class LoanSalarySlip {
  @Id @Column(length = 40) private String id;
  @Column(nullable = false) private Long loanAccountId;
  @Column(nullable = false, length = 7) private String salaryMonth;
  @Column(nullable = false, length = 160) private String fileName;
  @Column(nullable = false, length = 80) private String mediaType;
  @Column(nullable = false) private Integer fileSize;
  @Column(nullable = false, length = 64) private String sha256;
  @Lob @Column(nullable = false) private byte[] fileContent;
  @Column(nullable = false) private LocalDateTime uploadedAt;
  @Column(length = 26) private String verifiedBy;
  private LocalDateTime verifiedAt;
}
