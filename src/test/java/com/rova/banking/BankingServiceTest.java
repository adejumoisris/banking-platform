package com.rova.banking;

import static org.assertj.core.api.Assertions.*;

import com.rova.banking.domain.*;
import com.rova.banking.model.LedgerEntry;
import com.rova.banking.model.Loan;
import com.rova.banking.service.AccountService;
import com.rova.banking.service.CustomerService;
import com.rova.banking.service.JournalService;
import com.rova.banking.service.LoanService;
import com.rova.banking.service.PaymentService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class BankingServiceTest {
  @Autowired CustomerService customers;
  @Autowired AccountService accounts;
  @Autowired PaymentService payments;
  @Autowired LoanService loans;
  @Autowired JournalService journal;

  private UUID account(String currency) {
    var c = customers.createCustomer("Test Customer", UUID.randomUUID() + "@example.com");
    customers.verifyCustomer(c.getId());
    return accounts.openAccount(c.getId(), currency).getId();
  }

  @Test
  void unverifiedCustomerCannotOpenAccount() {
    var c = customers.createCustomer("Pending", UUID.randomUUID() + "@example.com");
    assertThatThrownBy(() -> accounts.openAccount(c.getId(), "NGN"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void transferIsBalancedAndRetryDoesNotMoveMoneyTwice() {
    var a = account("NGN");
    var b = account("NGN");
    payments.post("fund", null, a, new BigDecimal("100.00"), PostingKind.DEPOSIT);
    var p = payments.post("transfer", a, b, new BigDecimal("35.00"), PostingKind.TRANSFER);
    assertThat(
            payments.post("transfer", a, b, new BigDecimal("35.00"), PostingKind.TRANSFER).getId())
        .isEqualTo(p.getId());
    assertThat(accounts.account(a).getBalance()).isEqualByComparingTo("65.00");
    assertThat(accounts.account(b).getBalance()).isEqualByComparingTo("35.00");
    var entries = journal.ledger(p.getId(), 0, 100);
    assertThat(entries).hasSize(2);
    assertThat(
            entries.stream()
                .filter(e -> e.getSide() == LedgerEntry.Side.DEBIT)
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(
            entries.stream()
                .filter(e -> e.getSide() == LedgerEntry.Side.CREDIT)
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  @Test
  void rejectsInsufficientFundsAndCurrencyMismatch() {
    var a = account("NGN");
    var b = account("USD");
    assertThatThrownBy(
            () -> payments.post("empty", a, null, BigDecimal.ONE, PostingKind.WITHDRAWAL))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> payments.post("fx", a, b, BigDecimal.ONE, PostingKind.TRANSFER))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectsChangedRetryAndFractionalMinorUnits() {
    var a = account("NGN");
    payments.post("one", null, a, BigDecimal.ONE, PostingKind.DEPOSIT);
    assertThatThrownBy(() -> payments.post("one", null, a, BigDecimal.TEN, PostingKind.DEPOSIT))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () -> payments.post("fraction", null, a, new BigDecimal("0.001"), PostingKind.DEPOSIT))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void loanDisbursesAndRepaysExactlyOnce() {
    var a = account("NGN");
    var l = loans.originate(a, new BigDecimal("100.00"));
    loans.disburse(l.getId());
    loans.disburse(l.getId());
    assertThat(accounts.account(a).getBalance()).isEqualByComparingTo("100.00");
    var p = loans.repay(l.getId(), "final", new BigDecimal("100.00"));
    assertThat(loans.repay(l.getId(), "final", new BigDecimal("100.00")).getId())
        .isEqualTo(p.getId());
    assertThat(loans.loan(l.getId()).getStatus()).isEqualTo(Loan.Status.CLOSED);
    assertThat(accounts.account(a).getBalance()).isEqualByComparingTo("0.00");
  }
}
