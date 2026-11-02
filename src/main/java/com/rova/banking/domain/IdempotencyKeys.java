package com.rova.banking.domain;

public final class IdempotencyKeys {
    private IdempotencyKeys() {}
    public static String validate(String key, int maxLength) {
        if (key == null || key.length() > maxLength || !key.matches("[A-Za-z0-9._:-]+")) {
            throw BusinessException.invalid("Invalid Idempotency-Key");
        }
        return key;
    }
    public static String external(String key) {
        validate(key, 100);
        if (key.startsWith("loan-disbursement:") || key.startsWith("repay:")) {
            throw BusinessException.invalid("Reserved idempotency key prefix");
        }
        return key;
    }
}
