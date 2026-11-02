package com.rova.banking.api;

import com.rova.banking.customer.CustomerService;
import com.rova.banking.api.Requests.*;
import com.rova.banking.api.Views.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService customers;
    public CustomerController(CustomerService customers) { this.customers = customers; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('OPERATIONS')")
    public CustomerView create(@Valid @RequestBody CustomerRequest request) {
        return CustomerView.of(customers.createCustomer(request.fullName(), request.email()));
    }
    @PostMapping("/{id}/verify") @PreAuthorize("hasRole('COMPLIANCE')")
    public CustomerView verify(@PathVariable UUID id) { return CustomerView.of(customers.verifyCustomer(id)); }
}
