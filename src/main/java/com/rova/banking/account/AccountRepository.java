package com.rova.banking.account;

import java.util.*;

public interface AccountRepository {
    Optional<Account> find(UUID id);
    Optional<Account> lock(UUID id);
    Account save(Account account);
}
