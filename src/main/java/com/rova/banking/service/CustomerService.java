package com.rova.banking.service;

import com.rova.banking.audit.ActivityRecorder;
import com.rova.banking.domain.NotFound;
import com.rova.banking.model.Customer;
import com.rova.banking.repository.CustomerRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(timeout = 15)
public class CustomerService {
  private final ActivityRecorder activity;
  private final CustomerRepository customers;
  private final Clock clock;

  public CustomerService(CustomerRepository customers, Clock clock, ActivityRecorder activity) {
    this.activity = activity;
    this.customers = customers;
    this.clock = clock;
  }

  public Customer createCustomer(String fullName, String email) {
    Customer customer = customers.save(Customer.create(fullName, email, clock.instant()));
    activity.record("customer.created", customer.getId());
    return customer;
  }

  public Customer verifyCustomer(UUID id) {
    Customer customer = customers.lock(id).orElseThrow(() -> NotFound.resource("Customer"));
    if (customer.getKycStatus() == Customer.Status.VERIFIED) return customer;
    customer.verify();
    activity.record("customer.verified", id);
    return customers.save(customer);
  }

  @Transactional(readOnly = true, timeout = 5)
  public Customer customer(UUID id) {
    return customers.find(id).orElseThrow(() -> NotFound.resource("Customer"));
  }
}
