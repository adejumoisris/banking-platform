package com.rova.banking.api;

import com.rova.banking.api.Requests.*;
import com.rova.banking.api.Views.*;
import com.rova.banking.domain.*;
import com.rova.banking.payment.PaymentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
  private final PaymentService payments;

  public PaymentController(PaymentService payments) {
    this.payments = payments;
  }

  @PostMapping("/accounts/{id}/deposits")
  @PreAuthorize("hasRole('TELLER')")
  public PostingView deposit(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody AmountRequest request) {
    return PostingView.of(
        payments.post(
            IdempotencyKeys.external(key), null, id, request.amount(), PostingKind.DEPOSIT));
  }

  @PostMapping("/accounts/{id}/withdrawals")
  @PreAuthorize("hasRole('TELLER')")
  public PostingView withdraw(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody AmountRequest request) {
    return PostingView.of(
        payments.post(
            IdempotencyKeys.external(key), id, null, request.amount(), PostingKind.WITHDRAWAL));
  }

  @PostMapping("/transfers")
  @PreAuthorize("hasRole('OPERATIONS')")
  public PostingView transfer(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody TransferRequest request) {
    return PostingView.of(
        payments.post(
            IdempotencyKeys.external(key),
            request.sourceId(),
            request.destinationId(),
            request.amount(),
            PostingKind.TRANSFER));
  }
}
