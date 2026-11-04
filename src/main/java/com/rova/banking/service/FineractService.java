package com.rova.banking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rova.banking.audit.ActivityRecorder;
import com.rova.banking.config.FineractSettings;
import com.rova.banking.domain.BusinessException;
import com.rova.banking.domain.NotFound;
import com.rova.banking.integration.FineractClient;
import com.rova.banking.integration.FineractFailure;
import com.rova.banking.model.FineractCommand;
import com.rova.banking.model.FineractCommand.Kind;
import com.rova.banking.model.FineractCommand.Status;
import com.rova.banking.repository.AccountRepository;
import com.rova.banking.repository.CustomerRepository;
import com.rova.banking.repository.FineractCommandRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "fineract.enabled", havingValue = "true")
public class FineractService {
  private final FineractCommandRepository commands;
  private final CustomerRepository customers;
  private final AccountRepository accounts;
  private final FineractClient client;
  private final FineractSettings settings;
  private final Clock clock;
  private final ObjectMapper json;
  private final ActivityRecorder activity;

  public FineractService(
      FineractCommandRepository commands,
      CustomerRepository customers,
      AccountRepository accounts,
      FineractClient client,
      FineractSettings settings,
      Clock clock,
      ObjectMapper json,
      ActivityRecorder activity) {
    this.commands = commands;
    this.customers = customers;
    this.accounts = accounts;
    this.client = client;
    this.settings = settings;
    this.clock = clock;
    this.json = json;
    this.activity = activity;
  }

  @Transactional(timeout = 15)
  public FineractCommand enqueueCustomer(UUID id) {
    var customer = customers.lock(id).orElseThrow(() -> NotFound.resource("Customer"));
    customer.requireVerified();
    var existing = commands.find(settings.scope(), Kind.CUSTOMER, id);
    if (existing.isPresent()) return existing.get();
    String externalId = "rova-customer-" + id;
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("externalId", externalId);
    payload.put("fullname", customer.getFullName());
    payload.put("legalFormId", 1);
    payload.put("officeId", settings.officeId());
    payload.put("active", true);
    payload.put("activationDate", LocalDate.now(clock).toString());
    payload.put("dateFormat", "yyyy-MM-dd");
    payload.put("locale", "en");
    return enqueue(Kind.CUSTOMER, id, externalId, payload);
  }

  @Transactional(timeout = 15)
  public FineractCommand enqueueAccount(UUID id) {
    var account = accounts.lock(id).orElseThrow(() -> NotFound.resource("Account"));
    account.requireActive();
    var existing = commands.find(settings.scope(), Kind.ACCOUNT, id);
    if (existing.isPresent()) return existing.get();
    var customer =
        commands
            .find(settings.scope(), Kind.CUSTOMER, account.getCustomerId())
            .filter(command -> command.status() == Status.SUCCEEDED)
            .orElseThrow(() -> BusinessException.rule("Provision the customer in Fineract first"));
    Long productId = settings.savingsProducts().get(account.getCurrency());
    if (productId == null)
      throw BusinessException.rule("No Fineract savings product configured for account currency");
    String externalId = "rova-account-" + id;
    Map<String, Object> payload =
        Map.of(
            "externalId",
            externalId,
            "clientId",
            customer.remoteId(),
            "productId",
            productId,
            "submittedOnDate",
            LocalDate.now(clock).toString(),
            "dateFormat",
            "yyyy-MM-dd",
            "locale",
            "en",
            "_currency",
            account.getCurrency());
    return enqueue(Kind.ACCOUNT, id, externalId, payload);
  }

  private FineractCommand enqueue(
      Kind kind, UUID id, String externalId, Map<String, Object> payload) {
    try {
      var command =
          new FineractCommand(
              UUID.randomUUID(),
              settings.scope(),
              kind,
              id,
              externalId,
              json.writeValueAsString(payload),
              Status.PENDING,
              0,
              false,
              null,
              null,
              null,
              clock.instant(),
              null);
      commands.insert(command, clock.instant());
      activity.record("fineract.queued", command.id());
      return command;
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not serialize provisioning command", ex);
    }
  }

  @Transactional(readOnly = true, timeout = 5)
  public FineractCommand command(UUID id) {
    return commands
        .find(settings.scope(), id)
        .orElseThrow(() -> NotFound.resource("Fineract command"));
  }

  @Transactional(timeout = 5)
  public FineractCommand reconcile(UUID id) {
    command(id);
    if (!commands.reconcile(settings.scope(), id, clock.instant())) {
      throw BusinessException.rule("Only commands awaiting review can be reconciled");
    }
    activity.record("fineract.reconciliation.requested", id);
    return command(id);
  }

  @Transactional(propagation = Propagation.NEVER)
  public void dispatchReady() {
    for (UUID id : commands.ready(settings.scope(), clock.instant())) dispatch(id);
  }

  @Transactional(propagation = Propagation.NEVER)
  public FineractCommand dispatch(UUID id) {
    command(id);
    var claimed = commands.claim(settings.scope(), id, clock.instant());
    if (claimed.isEmpty()) return command(id);
    FineractCommand command = claimed.get();
    if (command.attempts() > 8) {
      finish(command, Status.REVIEW, null, "RETRY_EXHAUSTED");
      return command(id);
    }
    try {
      var existing = client.find(command);
      if (existing.isPresent()) {
        finish(command, Status.SUCCEEDED, existing.getAsLong(), null);
      } else if (command.createAttempted()) {
        // A timed-out or crashed POST may still commit remotely. Never blindly repeat it.
        retry(command, "AWAITING_REMOTE_CONFIRMATION");
      } else {
        client.validate(command);
        // Commit the intent BEFORE sending a POST; crash recovery becomes lookup-only.
        if (commands.prepareCreate(command, clock.instant())) {
          long remoteId = client.create(command);
          finish(command, Status.SUCCEEDED, remoteId, null);
        }
      }
    } catch (FineractFailure ex) {
      if (ex.retryable()) retry(command, ex.code());
      else
        finish(
            command,
            "REMOTE_IDENTITY_MISMATCH".equals(ex.code()) ? Status.REVIEW : Status.FAILED,
            null,
            ex.code());
    } catch (RuntimeException ex) {
      finish(command, Status.REVIEW, null, "LOCAL_PROCESSING_FAILURE");
    }
    return command(id);
  }

  private void retry(FineractCommand command, String error) {
    finish(command, command.attempts() >= 8 ? Status.REVIEW : Status.RETRY, null, error);
  }

  private void finish(FineractCommand command, Status status, Long remoteId, String error) {
    long delay = Math.min(300, 5L << Math.min(command.attempts() - 1, 6));
    commands.finish(
        command, status, remoteId, error, clock.instant().plusSeconds(delay), clock.instant());
  }
}
