package com.rova.banking.repository;

import com.rova.banking.model.Customer;
import java.util.*;

public interface CustomerRepository {
  Optional<Customer> find(UUID id);

  Optional<Customer> lock(UUID id);

  Customer save(Customer customer);
}
