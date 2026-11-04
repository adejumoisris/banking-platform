package com.rova.banking.integration;

/** Only sanitized codes cross the adapter boundary; remote bodies may contain personal data. */
public final class FineractFailure extends RuntimeException {
  private final String code;
  private final boolean retryable;

  public FineractFailure(String code, boolean retryable) {
    super(code);
    this.code = code;
    this.retryable = retryable;
  }

  public String code() {
    return code;
  }

  public boolean retryable() {
    return retryable;
  }
}
