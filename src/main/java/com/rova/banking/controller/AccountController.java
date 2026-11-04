package com.rova.banking.controller;

import com.rova.banking.dto.Requests.*;
import com.rova.banking.dto.Views.*;
import com.rova.banking.service.AccountService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
  private final AccountService accounts;

  public AccountController(AccountService accounts) {
    this.accounts = accounts;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('OPERATIONS')")
  public AccountView open(@Valid @RequestBody AccountRequest request) {
    return AccountView.of(accounts.openAccount(request.customerId(), request.currency()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('OPERATIONS','AUDITOR')")
  public AccountView get(@PathVariable UUID id) {
    return AccountView.of(accounts.account(id));
  }
}
