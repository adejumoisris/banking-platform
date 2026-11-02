package com.rova.banking.customer;

import java.util.*;

public interface CustomerRepository {
    Optional<Customer> find(UUID id);
    Optional<Customer> lock(UUID id);
    Customer save(Customer customer);
}
