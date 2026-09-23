-- Private supporting documents, never served from a public/static directory.
CREATE TABLE loan_salary_slips (
 id VARCHAR2(40) PRIMARY KEY,
 loan_account_id NUMBER NOT NULL REFERENCES accounts(id),
 salary_month VARCHAR2(7) NOT NULL,
 file_name VARCHAR2(160) NOT NULL,
 media_type VARCHAR2(80) NOT NULL,
 file_size NUMBER(10) NOT NULL,
 sha256 VARCHAR2(64) NOT NULL,
 file_content BLOB NOT NULL,
 uploaded_at TIMESTAMP NOT NULL,
 verified_by VARCHAR2(26) REFERENCES customers(user_id),
 verified_at TIMESTAMP,
 CONSTRAINT uk_loan_salary_month UNIQUE (loan_account_id, salary_month),
 CONSTRAINT uk_loan_salary_content UNIQUE (loan_account_id, sha256),
 CONSTRAINT ck_loan_salary_size CHECK (file_size BETWEEN 1 AND 5242880)
);
