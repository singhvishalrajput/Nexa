package com.nexa.api.service;
import com.nexa.api.beans.Customer;


import com.nexa.api.beans.Customer;
import java.time.*;
import java.util.*;

public interface CustomerService {
  Customer create(Customer customer);

  List<Customer> getAll();

  Customer getById(Long id);

  Customer getByEmail(String email);

  List<Customer> search(String name);

  List<Customer> registered(LocalDate from, LocalDate to);

  Customer update(Long id, Customer customer);
}
