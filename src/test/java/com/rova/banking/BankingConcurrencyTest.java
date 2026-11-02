package com.rova.banking;

import static org.assertj.core.api.Assertions.*;

import com.rova.banking.account.AccountService;
import com.rova.banking.customer.CustomerService;
import com.rova.banking.domain.*;
import com.rova.banking.ledger.*;
import com.rova.banking.loan.*;
import com.rova.banking.payment.PaymentService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class BankingConcurrencyTest {
  @Autowired CustomerService customers;
  @Autowired AccountService accounts;
  @Autowired PaymentService payments;
  @Autowired LoanService loans;
  @Autowired JournalService journal;

  private UUID account() {
    var c = customers.createCustomer("Concurrent Test", UUID.randomUUID() + "@example.com");
    customers.verifyCustomer(c.getId());
    return accounts.openAccount(c.getId(), "NGN").getId();
  }

  @Test
  void competingWithdrawalsCannotOverdraw() throws Exception {
    UUID account = account();
    payments.post(
        UUID.randomUUID().toString(), null, account, new BigDecimal("100.00"), PostingKind.DEPOSIT);
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    Callable<Boolean> task =
        () -> {
          gate.await();
          try {
            payments.post(
                UUID.randomUUID().toString(),
                account,
                null,
                new BigDecimal("80.00"),
                PostingKind.WITHDRAWAL);
            return true;
          } catch (BusinessException ex) {
            return false;
          }
        };
    try {
      var first = pool.submit(task);
      var second = pool.submit(task);
      gate.countDown();
      assertThat(
              (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                  + (second.get(15, TimeUnit.SECONDS) ? 1 : 0))
          .isEqualTo(1);
      assertThat(accounts.account(account).getBalance()).isEqualByComparingTo("20.00");
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void failedTransferRollsBackBothBalances() {
    UUID source = account(), destination = account();
    payments.post(UUID.randomUUID().toString(), null, source, BigDecimal.ONE, PostingKind.DEPOSIT);
    payments.post(
        UUID.randomUUID().toString(),
        null,
        destination,
        new BigDecimal("99999999999999999.99"),
        PostingKind.DEPOSIT);
    assertThatThrownBy(
            () ->
                payments.post(
                    UUID.randomUUID().toString(),
                    source,
                    destination,
                    BigDecimal.ONE,
                    PostingKind.TRANSFER))
        .isInstanceOf(RuntimeException.class);
    assertThat(accounts.account(source).getBalance()).isEqualByComparingTo("1.00");
    assertThat(accounts.account(destination).getBalance())
        .isEqualByComparingTo("99999999999999999.99");
  }

  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @Test
  void lockedPostingRefreshesAnEarlierUnlockedRead() throws Exception {
    UUID id = account();
    var read = new CountDownLatch(1);
    var funded = new CountDownLatch(1);
    var pool = Executors.newSingleThreadExecutor();
    try {
      var withdrawal =
          pool.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .execute(
                          status -> {
                            accounts.account(
                                id); // Cache an initial zero balance in this persistence context.
                            read.countDown();
                            try {
                              if (!funded.await(15, TimeUnit.SECONDS))
                                throw new IllegalStateException("Funding timed out");
                            } catch (InterruptedException ex) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(ex);
                            }
                            return payments
                                .post(
                                    UUID.randomUUID().toString(),
                                    id,
                                    null,
                                    BigDecimal.ONE,
                                    PostingKind.WITHDRAWAL)
                                .getId();
                          }));
      assertThat(read.await(15, TimeUnit.SECONDS)).isTrue();
      payments.post(UUID.randomUUID().toString(), null, id, BigDecimal.TEN, PostingKind.DEPOSIT);
      funded.countDown();
      assertThat(withdrawal.get(15, TimeUnit.SECONDS)).isNotNull();
      assertThat(accounts.account(id).getBalance()).isEqualByComparingTo("9.00");
    } finally {
      funded.countDown();
      pool.shutdownNow();
    }
  }
}
