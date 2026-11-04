package com.rova.banking.repository;

import com.rova.banking.model.PageResult;
import com.rova.banking.model.StaffDirectory.*;
import java.time.Instant;
import java.util.UUID;

public interface StaffDirectoryRepository {
  PageResult<CustomerRow> customers(String query, String status, int page, int size);

  PageResult<AccountRow> accounts(
      String query, UUID customerId, String currency, String status, int page, int size);

  PageResult<LoanRow> loans(String query, UUID accountId, String status, int page, int size);

  Dashboard dashboard(Instant now);
}
