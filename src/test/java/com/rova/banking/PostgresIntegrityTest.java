package com.rova.banking;

import com.rova.banking.customer.CustomerService;
import com.rova.banking.account.AccountService;
import com.rova.banking.payment.PaymentService;
import com.rova.banking.domain.PostingKind;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles({"local", "test"})
@EnabledIfEnvironmentVariable(named="TEST_DATABASE_DRIVER", matches="org.postgresql.Driver")
class PostgresIntegrityTest {
    @Autowired CustomerService customers;
    @Autowired AccountService accounts;
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private UUID account() {
        var customer = customers.createCustomer("Postgres Test", UUID.randomUUID() + "@example.com");
        customers.verifyCustomer(customer.getId());
        return accounts.openAccount(customer.getId(), "NGN").getId();
    }
    @Test void rejectsUnbalancedPostingAtCommit() {
        UUID account = account();
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status ->
            jdbc.update("insert into posting(id, idempotency_key, destination_id, amount, currency, kind, created_at) values (?,?,?,10,'NGN','DEPOSIT',CURRENT_TIMESTAMP)", id, id.toString(), account)
        )).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select count(*) from posting where id = ?", Long.class, id)).isZero();
    }
    @Test void postedJournalAndAuditCannotBeMutated() {
        UUID account = account();
        var posting = payments.post(UUID.randomUUID().toString(), null, account, BigDecimal.TEN, PostingKind.DEPOSIT);
        assertThatThrownBy(() -> jdbc.update("update ledger_entry set amount = 1 where posting_id = ?", posting.getId())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("delete from posting where id = ?", posting.getId())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("delete from audit_event where aggregate_id = ?", posting.getId())).isInstanceOf(RuntimeException.class);
    }
}
