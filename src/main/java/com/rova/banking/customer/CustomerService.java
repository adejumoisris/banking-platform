package com.rova.banking.customer;

import com.rova.banking.domain.NotFound;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(timeout = 15)
public class CustomerService {
    private final CustomerRepository customers;
    private final Clock clock;
    public CustomerService(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }
    public Customer createCustomer(String fullName, String email) {
        return customers.save(Customer.create(fullName, email, clock.instant()));
    }
    public Customer verifyCustomer(UUID id) {
        Customer customer = customers.lock(id).orElseThrow(() -> NotFound.resource("Customer"));
        customer.verify();
        return customers.save(customer);
    }
}
