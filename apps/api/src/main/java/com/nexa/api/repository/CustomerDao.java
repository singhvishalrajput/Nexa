package com.nexa.api.repository;
import com.nexa.api.beans.Customer;


import com.nexa.api.beans.Customer;
import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerDao extends JpaRepository<Customer, Long> {
  Optional<Customer> findByUserId(String userId);

  Optional<Customer> findByEmail(String email);

  List<Customer> findByFullNameContainingIgnoreCase(String name);

  List<Customer> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

  boolean existsByEmail(String email);

  boolean existsByPhoneNumber(String phoneNumber);
}
