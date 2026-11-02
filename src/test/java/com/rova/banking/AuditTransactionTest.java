package com.rova.banking;

import com.rova.banking.customer.CustomerService;
import com.rova.banking.account.AccountService;
import com.rova.banking.payment.PaymentService;
import com.rova.banking.domain.PostingKind;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles({"local", "test"})
class AuditTransactionTest {
    @Autowired CustomerService customers;
    @Autowired AccountService accounts;
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private UUID account() {
        var customer = customers.createCustomer("Audit Test", UUID.randomUUID() + "@example.com");
        customers.verifyCustomer(customer.getId());
        return accounts.openAccount(customer.getId(), "NGN").getId();
    }

    @Test @WithMockUser(username="staff-123")
    void retryProducesOneJournalAndOneAuditEvent() {
        UUID account = account();
        String key = UUID.randomUUID().toString();
        var posting = payments.post(key, null, account, BigDecimal.TEN, PostingKind.DEPOSIT);
        assertThat(payments.post(key, null, account, BigDecimal.TEN, PostingKind.DEPOSIT).getId()).isEqualTo(posting.getId());
        assertThat(jdbc.queryForObject("select count(*) from ledger_entry where posting_id = ?", Long.class, posting.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where aggregate_id = ?", Long.class, posting.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select actor from audit_event where aggregate_id = ?", String.class, posting.getId())).isEqualTo("staff-123");
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where aggregate_id = ?", Long.class, posting.getId())).isEqualTo(1);
    }

    @Test void rollbackRemovesBalanceJournalAuditAndOutboxTogether() {
        UUID account = account();
        AtomicReference<UUID> postingId = new AtomicReference<>();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            postingId.set(payments.post(UUID.randomUUID().toString(), null, account, BigDecimal.TEN, PostingKind.DEPOSIT).getId());
            throw new IllegalStateException("Simulated downstream failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(accounts.account(account).getBalance()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("select count(*) from posting where id = ?", Long.class, postingId.get())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ledger_entry where posting_id = ?", Long.class, postingId.get())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_event where aggregate_id = ?", Long.class, postingId.get())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where aggregate_id = ?", Long.class, postingId.get())).isZero();
    }
}
