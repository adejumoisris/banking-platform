package com.rova.banking.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test
  void usesExactMinorUnits() {
    assertThat(
            new Money(new BigDecimal("0.10"), "NGN")
                .add(new Money(new BigDecimal("0.20"), "NGN"))
                .amount())
        .isEqualByComparingTo("0.30");
    assertThatThrownBy(() -> new Money(new BigDecimal("0.001"), "NGN"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void preventsOverdraftAndOverflow() {
    assertThatThrownBy(
            () -> new Money(BigDecimal.ONE, "NGN").subtract(new Money(BigDecimal.TEN, "NGN")))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () -> new Money(Money.MAX_AMOUNT, "NGN").add(new Money(BigDecimal.ONE, "NGN")))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectsCurrencyMismatchAndZeroPayments() {
    assertThatThrownBy(() -> new Money(BigDecimal.ONE, "NGN").add(new Money(BigDecimal.ONE, "USD")))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> Money.positive(BigDecimal.ZERO, "NGN"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void postingTypeDefinesLegalAccountShape() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> PostingKind.DEPOSIT.validateAccounts(id, id))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> PostingKind.TRANSFER.validateAccounts(id, id))
        .isInstanceOf(BusinessException.class);
    assertThatCode(() -> PostingKind.DEPOSIT.validateAccounts(null, id)).doesNotThrowAnyException();
  }
}
