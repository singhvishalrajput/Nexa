package com.ofss.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ofss.beans.CustomerCredential;

public interface CustomerCredentialDao extends JpaRepository<CustomerCredential, Long> {
	Optional<CustomerCredential> findByCustomerEmail(String email);
}
