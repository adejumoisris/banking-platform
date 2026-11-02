package com.rova.banking.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class Requests {
  private Requests() {}

  public record CustomerRequest(
      @NotBlank @Size(max = 160) String fullName, @NotBlank @Email @Size(max = 254) String email) {}

  public record AccountRequest(
      @NotNull UUID customerId, @NotNull @Pattern(regexp = "NGN|USD|GBP|EUR") String currency) {}

  public record AmountRequest(
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}

  public record TransferRequest(
      @NotNull UUID sourceId,
      @NotNull UUID destinationId,
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}

  public record LoanRequest(
      @NotNull UUID accountId,
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal principal) {}
}
