package com.rova.banking.account;

import com.rova.banking.audit.ActivityRecorder;
import com.rova.banking.customer.CustomerRepository;
import com.rova.banking.domain.NotFound;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(timeout = 15)
public class AccountService {
  private final ActivityRecorder activity;
  private final AccountRepository accounts;
  private final CustomerRepository customers;

  public AccountService(
      AccountRepository accounts, CustomerRepository customers, ActivityRecorder activity) {
    this.activity = activity;
    this.accounts = accounts;
    this.customers = customers;
  }

  public Account openAccount(UUID customerId, String currency) {
    customers.lock(customerId).orElseThrow(() -> NotFound.resource("Customer")).requireVerified();
    Account account = accounts.save(Account.open(customerId, currency));
    activity.record("account.opened", account.getId());
    return account;
  }

  @Transactional(readOnly = true, timeout = 5)
  public Account account(UUID id) {
    return accounts.find(id).orElseThrow(() -> NotFound.resource("Account"));
  }
}
