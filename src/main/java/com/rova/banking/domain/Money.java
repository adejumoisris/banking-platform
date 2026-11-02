package com.rova.banking.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** Exact minor-unit arithmetic. FX and currencies with different scales need separate policies. */
public record Money(BigDecimal amount, String currency) {
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");
    private static final Set<String> CURRENCIES = Set.of("NGN", "USD", "GBP", "EUR");

    public Money {
        if (amount == null || currency == null || !CURRENCIES.contains(currency)) {
            throw BusinessException.invalid("A supported currency and amount are required");
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw BusinessException.invalid("Amount must use whole minor units");
        }
        if (amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(BusinessException.Code.CAPACITY_EXCEEDED,
                    "Amount is outside the supported range");
        }
    }

    public static Money positive(BigDecimal amount, String currency) {
        Money money = new Money(amount, currency);
        if (money.amount.signum() == 0) throw BusinessException.invalid("Amount must be positive");
        return money;
    }

    public Money add(Money other) {
        sameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        sameCurrency(other);
        if (amount.compareTo(other.amount) < 0) {
            throw new BusinessException(BusinessException.Code.INSUFFICIENT_FUNDS, "Insufficient funds");
        }
        return new Money(amount.subtract(other.amount), currency);
    }

    private void sameCurrency(Money other) {
        if (!currency.equals(other.currency)) throw BusinessException.rule("Cross-currency posting is not supported");
    }
}
