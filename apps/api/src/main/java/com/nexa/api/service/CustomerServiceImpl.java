package com.nexa.api.service;
import com.nexa.api.beans.Customer;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.CustomerDao;


import com.nexa.api.beans.Customer;
import com.nexa.api.repository.CustomerDao;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CustomerServiceImpl implements CustomerService {
  @Autowired CustomerDao dao;

  public Customer create(Customer c) {
    required(c.getFullName(), "fullName");
    required(c.getEmail(), "email");
    required(c.getDateOfBirth(), "dateOfBirth");
    required(c.getAddress(), "address");
    if (dao.existsByEmail(c.getEmail())) throw new InvalidRequestException("Email already exists");
    if (c.getPhoneNumber() != null && dao.existsByPhoneNumber(c.getPhoneNumber()))
      throw new InvalidRequestException("Phone number already exists");
    return dao.save(c);
  }

  public List<Customer> getAll() {
    return dao.findAll();
  }

  public Customer getById(Long id) {
    return dao.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
  }

  public Customer getByEmail(String email) {
    return dao.findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("Customer not found for email: " + email));
  }

  public List<Customer> search(String name) {
    return dao.findByFullNameContainingIgnoreCase(name);
  }

  public List<Customer> registered(LocalDate from, LocalDate to) {
    if (to.isBefore(from)) throw new InvalidRequestException("'to' must be on or after 'from'");
    return dao.findByCreatedAtBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
  }

  public Customer update(Long id, Customer input) {
    required(input.getFullName(), "fullName");
    required(input.getAddress(), "address");
    Customer c = getById(id);
    if (input.getPhoneNumber() != null
        && !input.getPhoneNumber().equals(c.getPhoneNumber())
        && dao.existsByPhoneNumber(input.getPhoneNumber()))
      throw new InvalidRequestException("Phone number already exists");
    c.setFullName(input.getFullName());
    c.setPhoneNumber(input.getPhoneNumber());
    c.setAddress(input.getAddress());
    return dao.save(c);
  }

  private void required(Object value, String field) {
    if (value == null || (value instanceof String s && s.isBlank()))
      throw new InvalidRequestException(field + " is required");
  }
}
