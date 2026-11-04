package com.rova.banking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rova.banking.config.FineractSettings;
import com.rova.banking.domain.BusinessException;
import com.rova.banking.integration.FineractClient;
import com.rova.banking.integration.FineractFailure;
import com.rova.banking.model.FineractCommand;
import com.rova.banking.model.FineractCommand.Status;
import com.rova.banking.repository.FineractCommandRepository;
import com.rova.banking.service.AccountService;
import com.rova.banking.service.CustomerService;
import com.rova.banking.service.FineractService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "fineract.enabled=true",
      "fineract.url=http://127.0.0.1:19099/api/v1",
      "fineract.tenant=contract",
      "fineract.username=test",
      "fineract.password=test-only",
      "fineract.office-id=1",
      "fineract.savings-products.NGN=7",
      "fineract.worker-enabled=false"
    })
@ActiveProfiles({"local", "test"})
@AutoConfigureMockMvc
class FineractServiceTest {
  @Autowired FineractService service;
  @Autowired CustomerService customers;
  @Autowired AccountService accounts;
  @Autowired FineractCommandRepository commands;
  @Autowired FineractSettings settings;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  @Autowired MockMvc mvc;
  @MockitoBean FineractClient remote;

  private UUID customer() {
    var customer = customers.createCustomer("Fineract Test", UUID.randomUUID() + "@example.com");
    customers.verifyCustomer(customer.getId());
    return customer.getId();
  }

  private void ready(FineractCommand command) {
    jdbc.update(
        "update fineract_command set available_at=? where id=?",
        Timestamp.from(Instant.now().minusSeconds(1)),
        command.id());
  }

  @Test
  void queueIsIdempotentAndTransactional() {
    UUID customer = customer();
    var first = service.enqueueCustomer(customer);
    assertThat(first.payload()).contains("\"legalFormId\":1");
    assertThat(service.enqueueCustomer(customer).id()).isEqualTo(first.id());
    verifyNoInteractions(remote);
    UUID other = customer();
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        status -> {
                          service.enqueueCustomer(other);
                          throw new IllegalStateException("rollback");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(commands.find(settings.scope(), FineractCommand.Kind.CUSTOMER, other)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_event where aggregate_id=? and action='fineract.queued'",
                Integer.class,
                first.id()))
        .isEqualTo(1);
  }

  @Test
  void provisioningRunsOutsideFinancialTransactionsAndKeepsMapping() {
    var command = service.enqueueCustomer(customer());
    when(remote.find(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return OptionalLong.empty();
            });
    when(remote.create(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(service.command(command.id()).createAttempted()).isTrue();
              return 101L;
            });
    var result = service.dispatch(command.id());
    assertThat(result.status()).isEqualTo(Status.SUCCEEDED);
    assertThat(result.remoteId()).isEqualTo(101L);
    service.dispatch(command.id());
    verify(remote, times(1)).create(any());
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(status -> service.dispatch(command.id())))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  }

  @Test
  void ambiguousPostRecoversByExternalIdWithoutSecondCreate() {
    var command = service.enqueueCustomer(customer());
    when(remote.find(any())).thenReturn(OptionalLong.empty(), OptionalLong.of(102));
    when(remote.create(any())).thenThrow(new FineractFailure("TRANSPORT", true));
    assertThat(service.dispatch(command.id()).status()).isEqualTo(Status.RETRY);
    assertThat(service.command(command.id()).createAttempted()).isTrue();
    ready(command);
    assertThat(service.dispatch(command.id()).remoteId()).isEqualTo(102L);
    verify(remote, times(1)).create(any());
  }

  @Test
  void ambiguousAbsenceRequiresReviewAndReconciliationCannotRepeatPost() {
    var command = service.enqueueCustomer(customer());
    when(remote.find(any())).thenReturn(OptionalLong.empty());
    when(remote.create(any())).thenThrow(new FineractFailure("HTTP_503", true));
    for (int attempt = 0; attempt < 8; attempt++) {
      ready(command);
      service.dispatch(command.id());
    }
    assertThat(service.command(command.id()).status()).isEqualTo(Status.REVIEW);
    service.reconcile(command.id());
    service.dispatch(command.id());
    verify(remote, times(1)).create(any());
    assertThat(service.command(command.id()).createAttempted()).isTrue();
  }

  @Test
  void staleWorkerCannotCreateOrOverwriteNewLease() {
    var command = service.enqueueCustomer(customer());
    Instant now = Instant.now();
    var first = commands.claim(settings.scope(), command.id(), now).orElseThrow();
    assertThat(commands.claim(settings.scope(), command.id(), now)).isEmpty();
    assertThat(commands.prepareCreate(first, now)).isTrue();
    var recovered =
        commands.claim(settings.scope(), command.id(), now.plusSeconds(91)).orElseThrow();
    assertThat(recovered.createAttempted()).isTrue();
    assertThat(commands.prepareCreate(first, now.plusSeconds(91))).isFalse();
    assertThat(commands.finish(first, Status.SUCCEEDED, 103L, null, now, now)).isFalse();
    assertThat(commands.finish(recovered, Status.SUCCEEDED, 103L, null, now, now)).isTrue();
  }

  @Test
  void permanentRejectionStopsAutomaticDispatch() {
    var command = service.enqueueCustomer(customer());
    when(remote.find(any())).thenReturn(OptionalLong.empty());
    when(remote.create(any())).thenThrow(new FineractFailure("HTTP_400", false));
    assertThat(service.dispatch(command.id()).status()).isEqualTo(Status.FAILED);
    ready(command);
    service.dispatch(command.id());
    verify(remote, times(1)).create(any());
  }

  @Test
  void accountUsesCustomerMappingAndCurrencyProductWithoutChangingBalance() {
    UUID customer = customer();
    var account = accounts.openAccount(customer, "NGN");
    assertThatThrownBy(() -> service.enqueueAccount(account.getId()))
        .isInstanceOf(BusinessException.class);
    when(remote.find(any())).thenReturn(OptionalLong.empty());
    when(remote.create(any())).thenReturn(104L, 204L);
    var clientCommand = service.enqueueCustomer(customer);
    service.dispatch(clientCommand.id());
    var command = service.enqueueAccount(account.getId());
    assertThat(command.payload())
        .contains("\"clientId\":104", "\"productId\":7", "\"_currency\":\"NGN\"");
    assertThat(service.dispatch(command.id()).remoteId()).isEqualTo(204L);
    assertThat(accounts.account(account.getId()).getBalance()).isZero();
    assertThat(commands.find("different-target", command.id())).isEmpty();
  }

  @Test
  @WithMockUser(roles = "AUDITOR")
  void auditorCanReadSanitizedStatusButCannotQueueOrDispatch() throws Exception {
    var command = service.enqueueCustomer(customer());
    mvc.perform(get("/api/v1/integrations/fineract/commands/" + command.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.payload").doesNotExist())
        .andExpect(jsonPath("$.scope").doesNotExist())
        .andExpect(jsonPath("$.leaseToken").doesNotExist());
    mvc.perform(post("/api/v1/integrations/fineract/customers/" + command.localId()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/integrations/fineract/commands/" + command.id() + "/dispatch"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousCannotAccessIntegration() throws Exception {
    mvc.perform(get("/api/v1/integrations/fineract/commands/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "AUDITOR")
  void commandDirectoryIsScopedAndDoesNotExposePayloads() throws Exception {
    var command = service.enqueueCustomer(customer());
    mvc.perform(get("/api/v1/integrations/fineract/commands").param("q", command.id().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(command.id().toString()))
        .andExpect(jsonPath("$.items[0].payload").doesNotExist())
        .andExpect(jsonPath("$.items[0].scope").doesNotExist())
        .andExpect(header().string("Cache-Control", "no-store"));
    assertThat(commands.list("different-scope", "", null, 0, 20).items()).isEmpty();
  }
}
