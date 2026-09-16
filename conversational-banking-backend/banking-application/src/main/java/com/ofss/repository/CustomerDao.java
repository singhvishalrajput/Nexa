package com.ofss.repository;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ofss.beans.Customer;

public interface CustomerDao extends JpaRepository<Customer, Long> {
	Optional<Customer> findByEmail(String email);

	List<Customer> findByFullNameContainingIgnoreCase(String name);

	List<Customer> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

	boolean existsByEmail(String email);

	boolean existsByPhoneNumber(String phoneNumber);
}
