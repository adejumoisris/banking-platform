package com.rova.banking.controller;

import com.rova.banking.dto.Views.CustomerView;
import com.rova.banking.model.PageResult;
import com.rova.banking.model.StaffDirectory.*;
import com.rova.banking.service.CustomerService;
import com.rova.banking.service.StaffDirectoryService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StaffDirectoryController {
  private final StaffDirectoryService directory;
  private final CustomerService customers;

  public StaffDirectoryController(StaffDirectoryService directory, CustomerService customers) {
    this.directory = directory;
    this.customers = customers;
  }

  private <T> ResponseEntity<T> result(T value) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }

  @GetMapping("/customers")
  @PreAuthorize("hasAnyRole('OPERATIONS','COMPLIANCE','AUDITOR')")
  public ResponseEntity<PageResult<CustomerRow>> customers(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return result(directory.customers(q, status, page, size));
  }

  @GetMapping("/customers/{id}")
  @PreAuthorize("hasAnyRole('OPERATIONS','COMPLIANCE','AUDITOR')")
  public ResponseEntity<CustomerView> customer(@PathVariable UUID id) {
    return result(CustomerView.of(customers.customer(id)));
  }

  @GetMapping("/accounts")
  @PreAuthorize("hasAnyRole('OPERATIONS','AUDITOR')")
  public ResponseEntity<PageResult<AccountRow>> accounts(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(defaultValue = "") String currency,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return result(directory.accounts(q, customerId, currency, status, page, size));
  }

  @GetMapping("/loans")
  @PreAuthorize("hasAnyRole('CREDIT','AUDITOR')")
  public ResponseEntity<PageResult<LoanRow>> loans(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return result(directory.loans(q, accountId, status, page, size));
  }

  @GetMapping("/dashboard")
  @PreAuthorize("hasAnyRole('OPERATIONS','AUDITOR')")
  public ResponseEntity<Dashboard> dashboard() {
    return result(directory.dashboard());
  }
}
