package com.rova.banking;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rova.banking.service.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class StaffDashboardTest {
  @Autowired MockMvc mvc;
  @Autowired CustomerService customers;
  @Autowired AccountService accounts;
  @Autowired PaymentService payments;
  @Autowired LoanService loans;
  @Autowired StaffDirectoryService directory;

  @Test
  void publicShellHasCspButRecordsRequireAuthentication() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    org.hamcrest.Matchers.containsString("script-src 'self'")));
    mvc.perform(get("/api/v1/ui/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.basicAuth").value(true))
        .andExpect(jsonPath("$.password").doesNotExist());
    mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(
      username = "reviewer",
      roles = {"AUDITOR"})
  void sessionAndDirectoryExposeSafeReadViews() throws Exception {
    var name = "Staff%_" + UUID.randomUUID();
    var customer = customers.createCustomer(name, "staff-" + UUID.randomUUID() + "@example.test");
    customers.verifyCustomer(customer.getId());
    var account = accounts.openAccount(customer.getId(), "NGN");
    accounts.openAccount(customer.getId(), "USD");
    payments.post(
        UUID.randomUUID().toString(),
        null,
        account.getId(),
        new BigDecimal("1234.56"),
        com.rova.banking.domain.PostingKind.DEPOSIT);
    loans.originate(account.getId(), new BigDecimal("200"));
    mvc.perform(get("/api/v1/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("reviewer"))
        .andExpect(jsonPath("$.roles[0]").value("AUDITOR"))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(get("/api/v1/customers").param("q", name))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.items[0].accountCount").value(2));
    mvc.perform(
            get("/api/v1/accounts")
                .param("customerId", customer.getId().toString())
                .param("currency", "NGN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].customerName").value(name))
        .andExpect(jsonPath("$.items[0].balance").value(1234.56));
    mvc.perform(get("/api/v1/loans").param("accountId", account.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    mvc.perform(get("/api/v1/dashboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activity.length()").value(7))
        .andExpect(jsonPath("$.balances").isArray())
        .andExpect(jsonPath("$.recentTransactions").isArray());
    assertThat(directory.accounts("", customer.getId(), "", "", 0, 20).items()).hasSize(2);
  }

  @Test
  @WithMockUser(roles = "TELLER")
  void tellerCannotReadDirectoriesOrCreateCustomers() throws Exception {
    mvc.perform(get("/api/v1/customers")).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/dashboard")).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/customers")
                .contentType("application/json")
                .content("{\"fullName\":\"No\",\"email\":\"no@example.test\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "OPERATIONS")
  void browserWritesRejectForeignOriginsAndAllowSameOrigin() throws Exception {
    var body =
        "{\"fullName\":\"Browser test\",\"email\":\"browser-"
            + UUID.randomUUID()
            + "@example.test\"}";
    mvc.perform(
            post("/api/v1/customers")
                .header("Origin", "https://foreign.example")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/customers")
                .header("Origin", "null")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/customers")
                .header("Sec-Fetch-Site", "cross-site")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/customers")
                .header("Origin", "http://localhost")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockUser(roles = {"OPERATIONS", "TELLER"})
  void exactDecimalStringsAreAcceptedAndRetryDoesNotPostTwice() throws Exception {
    var c =
        customers.createCustomer("Exact UI test", "exact-" + UUID.randomUUID() + "@example.test");
    customers.verifyCustomer(c.getId());
    var a = accounts.openAccount(c.getId(), "NGN");
    var key = UUID.randomUUID().toString();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/v1/accounts/" + a.getId() + "/deposits")
                  .header("Idempotency-Key", key)
                  .contentType("application/json")
                  .content("{\"amount\":\"999999999999999.99\"}"))
          .andExpect(status().isOk());
    assertThat(accounts.account(a.getId()).getBalance()).isEqualByComparingTo("999999999999999.99");
  }

  @Test
  @WithMockUser(roles = "AUDITOR")
  void invalidPagingAndFiltersFailClearly() throws Exception {
    mvc.perform(get("/api/v1/customers").param("size", "101")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/accounts").param("currency", "XYZ"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/loans").param("page", "-1")).andExpect(status().isBadRequest());
  }
}
