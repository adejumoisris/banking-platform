package com.rova.banking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rova.banking.domain.BusinessException;
import com.rova.banking.domain.PostingKind;
import com.rova.banking.model.AccountStatement;
import com.rova.banking.model.AccountTransaction;
import com.rova.banking.model.Posting;
import com.rova.banking.repository.AccountStatementRepository;
import com.rova.banking.service.AccountService;
import com.rova.banking.service.AccountStatementService;
import com.rova.banking.service.CustomerService;
import com.rova.banking.service.LoanService;
import com.rova.banking.service.PaymentService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class AccountStatementTest {
  private static final Instant FROM = Instant.parse("2026-01-02T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-01-03T00:00:00Z");
  @Autowired CustomerService customers;
  @Autowired AccountService accounts;
  @Autowired PaymentService payments;
  @Autowired LoanService loans;
  @Autowired AccountStatementService statements;
  @Autowired MockMvc mvc;
  @MockitoBean Clock clock;
  @MockitoSpyBean AccountStatementRepository repository;

  @BeforeEach
  void clock() {
    when(clock.instant()).thenReturn(FROM.minusSeconds(172800));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  }

  private UUID account() {
    var customer = customers.createCustomer("Statement Test", UUID.randomUUID() + "@example.com");
    customers.verifyCustomer(customer.getId());
    return accounts.openAccount(customer.getId(), "NGN").getId();
  }

  private Posting post(Instant at, UUID source, UUID destination, String amount, PostingKind type) {
    when(clock.instant()).thenReturn(at);
    return payments.post(
        UUID.randomUUID().toString(), source, destination, new BigDecimal(amount), type);
  }

  private record Scenario(UUID sender, UUID recipient, Posting transfer, Posting withdrawal) {}

  private Scenario scenario() {
    UUID sender = account();
    UUID recipient = account();
    UUID unrelated = account();
    post(FROM.minusSeconds(1), null, sender, "100.00", PostingKind.DEPOSIT);
    Posting transfer = post(FROM, sender, recipient, "30.00", PostingKind.TRANSFER);
    payments.post(
        transfer.getIdempotencyKey(),
        sender,
        recipient,
        new BigDecimal("30.00"),
        PostingKind.TRANSFER);
    Posting withdrawal = post(FROM.plusSeconds(60), sender, null, "20.00", PostingKind.WITHDRAWAL);
    post(TO, null, sender, "10.00", PostingKind.DEPOSIT);
    post(FROM.plusSeconds(120), null, unrelated, "999.00", PostingKind.DEPOSIT);
    when(clock.instant()).thenReturn(TO.plusSeconds(60));
    return new Scenario(sender, recipient, transfer, withdrawal);
  }

  @Test
  void statementReconstructsHistoricalBalanceFromLedgerWithExclusiveEnd() {
    var fixture = scenario();
    var statement = statements.statement(fixture.sender(), FROM, TO, 0, 1);
    assertThat(statement.totals().openingBalance()).isEqualByComparingTo("100.00");
    assertThat(statement.totals().totalMoneyIn()).isZero();
    assertThat(statement.totals().totalMoneyOut()).isEqualByComparingTo("50.00");
    assertThat(statement.totals().closingBalance()).isEqualByComparingTo("50.00");
    assertThat(statement.totals().transactionCount()).isEqualTo(2);
    assertThat(statement.totalPages()).isEqualTo(2);
    assertThat(statement.transactions())
        .extracting(AccountTransaction::reference)
        .containsExactly(fixture.transfer().getId());
    assertThat(accounts.account(fixture.sender()).getBalance()).isEqualByComparingTo("60.00");
    assertThat(statements.statement(fixture.sender(), FROM, TO, 1, 1).totals())
        .isEqualTo(statement.totals());
  }

  @Test
  void historyPaginatesNewestFirstWithCorrectTransferDirectionAndNoReplayDuplicates() {
    var fixture = scenario();
    var first = statements.history(fixture.sender(), FROM, TO, 0, 1);
    var second = statements.history(fixture.sender(), FROM, TO, 1, 1);
    assertThat(first.totals().transactionCount()).isEqualTo(2);
    assertThat(first.transactions())
        .extracting(AccountTransaction::reference)
        .containsExactly(fixture.withdrawal().getId());
    var outgoing = second.transactions().get(0);
    assertThat(outgoing.reference()).isEqualTo(fixture.transfer().getId());
    assertThat(outgoing.counterpartyAccountId()).isEqualTo(fixture.recipient());
    assertThat(outgoing.moneyIn()).isZero();
    assertThat(outgoing.moneyOut()).isEqualByComparingTo("30.00");
    var incoming = statements.history(fixture.recipient(), FROM, TO, 0, 100).transactions();
    assertThat(incoming).hasSize(1);
    assertThat(incoming.get(0).moneyIn()).isEqualByComparingTo("30.00");
    assertThat(incoming.get(0).moneyOut()).isZero();
    assertThat(incoming.get(0).counterpartyAccountId()).isEqualTo(fixture.sender());
  }

  @Test
  void historySupportsUnboundedAndOneSidedDateFilters() {
    var fixture = scenario();
    assertThat(statements.history(fixture.sender(), null, null, 0, 100).totals().transactionCount())
        .isEqualTo(4);
    var before = statements.history(fixture.sender(), null, FROM, 0, 100);
    assertThat(before.totals().transactionCount()).isEqualTo(1);
    assertThat(before.transactions().get(0).moneyIn()).isEqualByComparingTo("100.00");
    var after = statements.history(fixture.sender(), TO, null, 0, 100);
    assertThat(after.totals().transactionCount()).isEqualTo(1);
    assertThat(after.transactions().get(0).moneyIn()).isEqualByComparingTo("10.00");
  }

  @Test
  void emptyPeriodCarriesForwardBalanceAndEmptyAccountsHaveZeroTotals() {
    UUID funded = account();
    post(FROM.minusSeconds(1), null, funded, "100.00", PostingKind.DEPOSIT);
    var statement = statements.statement(funded, FROM, TO, 0, 50);
    assertThat(statement.transactions()).isEmpty();
    assertThat(statement.totalPages()).isZero();
    assertThat(statement.totals().openingBalance()).isEqualByComparingTo("100.00");
    assertThat(statement.totals().closingBalance()).isEqualByComparingTo("100.00");
    var empty = statements.statement(account(), FROM, TO, 0, 50);
    assertThat(empty.totals().openingBalance()).isZero();
    assertThat(empty.totals().closingBalance()).isZero();
    assertThat(statements.history(funded, FROM, TO, 5, 10).transactions()).isEmpty();
  }

  @Test
  void timestampTiesUseStableNonOverlappingPagination() {
    UUID id = account();
    for (int i = 0; i < 5; i++) post(FROM, null, id, "1.00", PostingKind.DEPOSIT);
    List<UUID> paged = new ArrayList<>();
    for (int page = 0; page < 3; page++) {
      statements
          .history(id, FROM, TO, page, 2)
          .transactions()
          .forEach(row -> paged.add(row.reference()));
    }
    var complete =
        statements.history(id, FROM, TO, 0, 100).transactions().stream()
            .map(AccountTransaction::reference)
            .toList();
    assertThat(paged).hasSize(5).doesNotHaveDuplicates().containsExactlyElementsOf(complete);
    assertThat(statements.history(id, FROM, TO, 0, 2).transactions())
        .isEqualTo(statements.history(id, FROM, TO, 0, 2).transactions());
  }

  @Test
  void loanDisbursementAndRepaymentAppearAsMoneyInAndOut() {
    UUID id = account();
    var loan = loans.originate(id, new BigDecimal("50.00"));
    when(clock.instant()).thenReturn(FROM);
    loans.disburse(loan.getId());
    loans.disburse(loan.getId());
    when(clock.instant()).thenReturn(FROM.plusSeconds(1));
    loans.repay(loan.getId(), UUID.randomUUID().toString(), new BigDecimal("20.00"));
    var statement = statements.statement(id, FROM, TO, 0, 50);
    assertThat(statement.transactions())
        .extracting(AccountTransaction::type)
        .containsExactly(PostingKind.LOAN_DISBURSEMENT, PostingKind.LOAN_REPAYMENT);
    assertThat(statement.totals().totalMoneyIn()).isEqualByComparingTo("50.00");
    assertThat(statement.totals().totalMoneyOut()).isEqualByComparingTo("20.00");
    assertThat(statement.totals().closingBalance()).isEqualByComparingTo("30.00");
  }

  @Test
  void csvExportsEveryTransactionWithOpeningAndClosingRows() {
    var fixture = scenario();
    String csv = statements.csv(fixture.sender(), FROM, TO);
    var lines = csv.lines().toList();
    assertThat(lines).hasSize(5);
    assertThat(lines.get(0)).contains("record_type", "money_in", "money_out", "balance");
    assertThat(lines.get(1)).startsWith("\"OPENING_BALANCE\"").endsWith("\"100.00\"");
    assertThat(lines.get(2)).contains(fixture.transfer().getId().toString(), "\"30.00\"");
    assertThat(lines.get(3)).contains(fixture.withdrawal().getId().toString(), "\"20.00\"");
    assertThat(lines.get(4))
        .startsWith("\"CLOSING_BALANCE\"")
        .endsWith("\"0.00\",\"50.00\",\"50.00\"");
    assertThat(csv).contains("\r\n").doesNotContain(fixture.transfer().getIdempotencyKey());
    assertThat(statements.csv(account(), FROM, TO).lines().count()).isEqualTo(3);
  }

  @Test
  void oversizedCsvIsRejectedBeforeLoadingRows() {
    UUID id = account();
    doReturn(new AccountStatement.Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 10001))
        .when(repository)
        .summarize(id, FROM, TO);
    assertThatThrownBy(() -> statements.csv(id, FROM, TO))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getCode()).isEqualTo(BusinessException.Code.INVALID_REQUEST));
    verify(repository, never())
        .transactions(eq(id), any(), any(), anyInt(), anyInt(), anyBoolean());
  }

  @ParameterizedTest
  @CsvSource({"-1,50", "100001,50", "0,0", "0,101"})
  void invalidPaginationIsRejected(int page, int size) {
    assertThatThrownBy(() -> statements.history(UUID.randomUUID(), null, null, page, size))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getCode()).isEqualTo(BusinessException.Code.INVALID_REQUEST));
  }

  @Test
  void invalidDateRangesAndMissingStatementBoundsAreRejected() {
    UUID id = UUID.randomUUID();
    for (Instant[] range :
        List.of(
            new Instant[] {TO, FROM},
            new Instant[] {FROM, FROM},
            new Instant[] {Instant.MIN, TO})) {
      assertThatThrownBy(() -> statements.history(id, range[0], range[1], 0, 10))
          .isInstanceOfSatisfying(
              BusinessException.class,
              error ->
                  assertThat(error.getCode()).isEqualTo(BusinessException.Code.INVALID_REQUEST));
    }
    assertThatThrownBy(() -> statements.statement(id, null, TO, 0, 10))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> statements.csv(id, FROM, null)).isInstanceOf(BusinessException.class);
  }

  @Test
  @WithMockUser(roles = "OPERATIONS")
  void operationsCanReadJsonHistoryAndStatementsWithoutInternalKeys() throws Exception {
    var fixture = scenario();
    mvc.perform(
            get("/api/v1/accounts/" + fixture.sender() + "/transactions")
                .param("from", FROM.toString())
                .param("to", TO.toString())
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(
            jsonPath("$.transactions[0].reference").value(fixture.withdrawal().getId().toString()))
        .andExpect(jsonPath("$.transactions[0].idempotencyKey").doesNotExist());
    mvc.perform(
            get("/api/v1/accounts/" + fixture.sender() + "/statement")
                .param("from", FROM.toString())
                .param("to", TO.toString())
                .param("page", "1")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingBalance").value(100))
        .andExpect(jsonPath("$.closingBalance").value(50))
        .andExpect(jsonPath("$.totalMoneyOut").value(50))
        .andExpect(jsonPath("$.transactions.length()").value(1));
  }

  @Test
  @WithMockUser(roles = "AUDITOR")
  void auditorCanDownloadCsvAttachment() throws Exception {
    var fixture = scenario();
    mvc.perform(
            get("/api/v1/accounts/" + fixture.sender() + "/statement.csv")
                .param("from", FROM.toString())
                .param("to", TO.toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv;charset=UTF-8"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"account-" + fixture.sender() + "-statement.csv\""))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("OPENING_BALANCE")));
  }

  @Test
  @WithMockUser(roles = "TELLER")
  void tellerCannotReadHistoryStatementsOrExports() throws Exception {
    for (String route : List.of("transactions", "statement", "statement.csv")) {
      mvc.perform(
              get("/api/v1/accounts/" + UUID.randomUUID() + "/" + route)
                  .param("from", FROM.toString())
                  .param("to", TO.toString()))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void anonymousCannotReadHistoryStatementsOrExports() throws Exception {
    for (String route : List.of("transactions", "statement", "statement.csv")) {
      mvc.perform(
              get("/api/v1/accounts/" + UUID.randomUUID() + "/" + route)
                  .param("from", FROM.toString())
                  .param("to", TO.toString()))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  @WithMockUser(roles = "AUDITOR")
  void apiRejectsMalformedFiltersAndMissingAccounts() throws Exception {
    String base = "/api/v1/accounts/" + UUID.randomUUID();
    mvc.perform(get(base + "/transactions").param("from", "not-a-date"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(base + "/statement")).andExpect(status().isBadRequest());
    mvc.perform(get(base + "/statement.csv").param("from", FROM.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(get(base + "/transactions").param("size", "101"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get(base + "/transactions").param("from", TO.toString()).param("to", FROM.toString()))
        .andExpect(status().isBadRequest());
    for (String route : List.of("transactions", "statement", "statement.csv")) {
      mvc.perform(get(base + "/" + route).param("from", FROM.toString()).param("to", TO.toString()))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_DRIVER", matches = "org.postgresql.Driver")
  void statementTotalsAndRowsShareOneSnapshotDuringConcurrentPosting() throws Exception {
    UUID id = account();
    post(FROM, null, id, "100.00", PostingKind.DEPOSIT);
    var summaryRead = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              var value = invocation.callRealMethod();
              summaryRead.countDown();
              if (!resume.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Snapshot test timed out");
              return value;
            })
        .when(repository)
        .summarize(id, FROM, TO);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var reading = executor.submit(() -> statements.statement(id, FROM, TO, 0, 50));
      assertThat(summaryRead.await(10, TimeUnit.SECONDS)).isTrue();
      post(FROM.plusSeconds(1), null, id, "10.00", PostingKind.DEPOSIT);
      resume.countDown();
      var snapshot = reading.get(10, TimeUnit.SECONDS);
      assertThat(snapshot.totals().transactionCount()).isEqualTo(1);
      assertThat(snapshot.transactions()).hasSize(1);
      assertThat(snapshot.totals().closingBalance()).isEqualByComparingTo("100.00");
      doCallRealMethod().when(repository).summarize(id, FROM, TO);
      var refreshed = statements.statement(id, FROM, TO, 0, 50);
      assertThat(refreshed.totals().transactionCount()).isEqualTo(2);
      assertThat(refreshed.transactions()).hasSize(2);
      assertThat(refreshed.totals().closingBalance()).isEqualByComparingTo("110.00");
    } finally {
      resume.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void subMicrosecondBoundsPreserveInclusiveStartAndExclusiveEnd() {
    UUID id = account();
    post(FROM, null, id, "1.00", PostingKind.DEPOSIT);
    Posting included = post(FROM.plusNanos(1000), null, id, "2.00", PostingKind.DEPOSIT);
    var statement = statements.statement(id, FROM.plusNanos(1), FROM.plusNanos(1001), 0, 50);
    assertThat(statement.totals().openingBalance()).isEqualByComparingTo("1.00");
    assertThat(statement.totals().totalMoneyIn()).isEqualByComparingTo("2.00");
    assertThat(statement.totals().closingBalance()).isEqualByComparingTo("3.00");
    assertThat(statement.transactions())
        .extracting(AccountTransaction::reference)
        .containsExactly(included.getId());
  }
}
