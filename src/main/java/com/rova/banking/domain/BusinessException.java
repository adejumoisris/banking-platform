package com.rova.banking.domain;

/** Business failures stay independent of HTTP, persistence and framework exceptions. */
public final class BusinessException extends RuntimeException {
    public enum Code {
        INVALID_REQUEST, RESOURCE_NOT_FOUND, RULE_VIOLATION,
        IDEMPOTENCY_CONFLICT, INSUFFICIENT_FUNDS, CAPACITY_EXCEEDED
    }

    private final Code code;

    public BusinessException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() { return code; }

    public static BusinessException invalid(String message) {
        return new BusinessException(Code.INVALID_REQUEST, message);
    }

    public static BusinessException rule(String message) {
        return new BusinessException(Code.RULE_VIOLATION, message);
    }
}
