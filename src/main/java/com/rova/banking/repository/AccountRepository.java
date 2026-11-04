package com.rova.banking.repository;

import com.rova.banking.model.Account;
import java.util.*;

public interface AccountRepository {
  Optional<Account> find(UUID id);

  Optional<Account> lock(UUID id);

  Account save(Account account);
}
